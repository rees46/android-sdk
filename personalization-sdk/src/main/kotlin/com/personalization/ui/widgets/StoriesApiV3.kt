package com.personalization.ui.widgets

import com.personalization.SDK
import com.personalization.stories.StoriesManager
import com.personalization.stories.models.Story
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONArray
import org.json.JSONObject

/**
 * Сторис V2 на API v3 (`PersonalizationStories.apiV3Domain`): лента, одна кампания по ссылке
 * и события — на `https://{apiV3Domain}/api/v3/storefront/stories/...`.
 *
 * Сеть SDK ходит только на свой хост, поэтому здесь свой маленький клиент. Нужны только
 * `shop_id` и `did` (`sid` сервер не просит); без `did` сервер отвечает 400, и запрос ждёт его
 * в той же очереди, что `getAsync` SDK, — до конца init.
 */
internal object StoriesApiV3 {

    /** Синхронный HTTP-запрос; зовётся не на главном потоке. Подменяется в тестах. */
    fun interface Http {
        fun send(method: String, url: String, body: String?): Response
    }

    data class Response(val code: Int, val body: String)

    var http: Http = Http(::sendHttp)

    /** Где идёт запрос: свой поток. Подменяется в тестах. */
    var runner: (Runnable) -> Unit = { Thread(it).start() }

    private const val PATH = "/api/v3/storefront/stories/"
    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val READ_TIMEOUT_MS = 10_000

    /** Хост без схемы, как `apiDomain`: схема и `/` в конце отбрасываются. */
    fun base(domain: String): String =
        "https://" + domain.trim().replace(Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://"), "").trimEnd('/')

    fun feedUrl(domain: String, code: String, shopId: String, did: String): String =
        base(domain) + PATH + segment(code) + identity(shopId, did)

    fun storyUrl(domain: String, link: StoriesShare.Link, shopId: String, did: String): String =
        base(domain) + PATH + segment(link.code) + "/story/" + link.storyId + identity(shopId, did) +
            (link.slideId?.let { "&slide_id=" + query(it) } ?: "")

    fun eventsUrl(domain: String, code: String): String = base(domain) + PATH + segment(code) + "/events"

    /** Тело события: `story_id` — число, как в ленте, `slide_id` — всегда строка. */
    fun eventBody(shopId: String, did: String, event: String, storyId: Int, slideId: String): JSONObject =
        JSONObject()
            .put("shop_id", shopId)
            .put("did", did)
            .put("event", event)
            .put("story_id", storyId)
            .put("slide_id", slideId)

    /**
     * Ответ GET: `{"status":"success","payload":{…}}` → сторис из `payload` тем же разбором,
     * что старая лента. JSON `null` (в v3 у товарных слайдов `background` и `preview`) убирается
     * до моделей: иначе `optString` отдал бы строку `"null"`.
     */
    fun parse(body: String): List<Story> {
        val json = envelope(body)
        val status = json.optString("status")
        require(status == "success") { "status $status" }
        val payload = json.optJSONObject("payload") ?: throw IllegalArgumentException("no payload")
        return StoriesLoader.parse(withoutNulls(payload) as JSONObject)
    }

    /**
     * Ответ API v3 `{"status", "message", "payload"}`. Не JSON или `status` `fail|error` —
     * исключение с `message` сервера: так ошибку видят лента и опросы.
     */
    fun envelope(body: String): JSONObject {
        val json = JSONObject(body)
        val status = json.optString("status")
        require(status != "fail" && status != "error") { json.optString("message").ifEmpty { "status $status" } }
        return json
    }

    fun withoutNulls(value: Any?): Any? = when (value) {
        is JSONObject -> JSONObject().also { result ->
            value.keys().forEach { key ->
                val item = value.opt(key)
                if (item != null && item !== JSONObject.NULL) result.put(key, withoutNulls(item))
            }
        }
        is JSONArray -> JSONArray().also { result ->
            for (index in 0 until value.length()) {
                val item = value.opt(index)
                if (item != null && item !== JSONObject.NULL) result.put(withoutNulls(item))
            }
        }
        else -> value
    }

    fun feed(sdk: SDK, domain: String, code: String, onLoaded: (List<Story>) -> Unit, onFailed: (Int, String?) -> Unit) =
        load(sdk, { shopId, did -> feedUrl(domain, code, shopId, did) }, onLoaded, onFailed)

    fun story(sdk: SDK, domain: String, link: StoriesShare.Link, onLoaded: (List<Story>) -> Unit, onFailed: (Int, String?) -> Unit) =
        load(sdk, { shopId, did -> storyUrl(domain, link, shopId, did) }, onLoaded, onFailed)

    /** Событие сторис; источник трекинга ставится, как у старого `track/stories`. */
    fun track(sdk: SDK, domain: String, code: String, event: String, storyId: Int, slideId: String) {
        runCatching { sdk.storiesManager.setTrackingSourceUseCase(StoriesManager.STORIES_SOURCE_TYPE, code) }
        whenDid(sdk) {
            val body = eventBody(shopId(sdk), sdk.getDid().orEmpty(), event, storyId, slideId)
            val response = post(eventsUrl(domain, code), body)
            if (response.code !in 200..299) SDK.error("stories v3: $event failed: ${response.code} ${response.body}")
        }
    }

    /** Ответ — на главном потоке. HTTP-ошибка или `status != success` — [onFailed]. */
    private fun load(sdk: SDK, url: (String, String) -> String, onLoaded: (List<Story>) -> Unit, onFailed: (Int, String?) -> Unit) {
        whenDid(sdk) {
            val response = try {
                call("GET", url(shopId(sdk), sdk.getDid().orEmpty()), null)
            } catch (throwable: Throwable) {
                MainThread.run { onFailed(StoriesLoader.ERROR_CODE, throwable.message) }
                return@whenDid
            }
            if (response.code !in 200..299) {
                MainThread.run { onFailed(response.code, response.body) }
                return@whenDid
            }
            val stories = try {
                parse(response.body)
            } catch (throwable: Throwable) {
                MainThread.run { onFailed(StoriesLoader.ERROR_CODE, "stories v3: ${throwable.message}") }
                return@whenDid
            }
            MainThread.run { onLoaded(stories) }
        }
    }

    /**
     * Задача — когда у SDK есть did: сразу или из очереди `getAsync` после init. Init мог
     * закончиться между проверкой и постановкой в очередь — тогда задача стартует сама; дважды
     * она не выполнится.
     */
    fun whenDid(sdk: SDK, task: () -> Unit) {
        val once = AtomicBoolean(false)
        val run = Runnable {
            if (!once.compareAndSet(false, true)) return@Runnable
            try {
                task()
            } catch (throwable: Throwable) {
                SDK.error("stories v3: request failed", throwable)
            }
        }
        try {
            if (sdk.getDid().isNullOrEmpty()) {
                sdk.addTaskToQueueUseCase.invoke(Thread(run))
                if (sdk.getDid().isNullOrEmpty()) return
            }
        } catch (throwable: Throwable) {
            SDK.error("stories v3: SDK is not ready", throwable)
        }
        runner(run)
    }

    fun shopId(sdk: SDK): String = sdk.getUserSettingsValueUseCase.getShopId()

    /** POST с JSON-телом — события и опросы; зовётся не на главном потоке. */
    fun post(url: String, body: JSONObject): Response = call("POST", url, body.toString())

    /** GET — для других клиентов API v3 (попапы); зовётся не на главном потоке. */
    fun get(url: String): Response = call("GET", url, null)

    /** Запрос с логом, как у сети SDK: строка в logcat и в `networkLogger`. */
    private fun call(method: String, url: String, body: String?): Response {
        val started = System.currentTimeMillis()
        val response = try {
            http.send(method, url, body)
        } catch (throwable: Throwable) {
            SDK.networkLogger?.onHttpCall(method, url, body, -1, null, System.currentTimeMillis() - started, throwable.message)
            throw throwable
        }
        SDK.debug("${response.code}: $method $url" + (body?.let { " with body: $it" } ?: ""))
        SDK.networkLogger?.onHttpCall(method, url, body, response.code, response.body, System.currentTimeMillis() - started, null)
        return response
    }

    private fun sendHttp(method: String, url: String, body: String?): Response {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("User-Agent", SDK.userAgent())
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code >= 400) connection.errorStream else connection.inputStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            return Response(code, text)
        } finally {
            connection.disconnect()
        }
    }

    /** Часть пути: пробел — `%20`, не `+`. */
    fun segment(value: String): String = query(value).replace("+", "%20")

    private fun query(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun identity(shopId: String, did: String): String = "?shop_id=" + query(shopId) + "&did=" + query(did)
}
