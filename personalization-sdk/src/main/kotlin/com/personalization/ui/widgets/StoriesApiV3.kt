package com.personalization.ui.widgets

import com.personalization.LogLevel
import com.personalization.SDK
import com.personalization.logs.LogCodes
import com.personalization.logs.SdkLogs
import com.personalization.stories.StoriesManager
import com.personalization.stories.models.Story
import com.personalization.core.BridgeApi
import com.personalization.core.internal.CoreHttp
import java.net.URL
import java.util.concurrent.TimeUnit
import okhttp3.CookieJar
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONArray
import org.json.JSONObject

/**
 * Сторис V2 на API v3 (`PersonalizationStories.apiV3Domain`): лента, одна кампания по ссылке
 * и события — на `https://{apiV3Domain}/api/v3/storefront/stories/...`.
 *
 * Сеть SDK ходит только на свой хост, поэтому здесь свой маленький клиент. Запросы несут то же,
 * что в веб-SDK ([Identity]): `shop_id`, `did`, `sid`, `segment` (A/B-группа устройства), `stream`
 * и `testmode=true` в тестовом режиме попапов v3. Без `did` сервер отвечает 400, и запрос ждёт его
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
    private val JSON = "application/json".toMediaType()

    /** Хост без схемы, как `apiDomain`: схема и `/` в конце отбрасываются. */
    fun base(domain: String): String =
        "https://" + domain.trim().replace(Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://"), "").trimEnd('/')

    /**
     * Кто спрашивает — поля запросов сторис и опросов, как в веб-SDK. GET несёт их в запросе, POST — в
     * теле. [segment] — A/B-группа устройства (`A`/`B`, без неё пусто); опросы его не шлют. [stream]
     * пустой не уходит, `testmode` уходит только включённым.
     */
    data class Identity(
        val shopId: String,
        val did: String,
        val sid: String = "",
        val segment: String = "",
        val stream: String = "",
        val testMode: Boolean = false
    ) {
        /** Запрос GET: `?shop_id=…&did=…&sid=…&segment=…[&stream=…][&testmode=true]`. */
        fun query(): String =
            "?shop_id=" + encode(shopId) + "&did=" + encode(did) + "&sid=" + encode(sid) + "&segment=" + encode(segment) +
                (if (stream.isNotEmpty()) "&stream=" + encode(stream) else "") +
                (if (testMode) "&testmode=true" else "")

        /** Поля тела POST; [withSegment] — `false` у опросов, как в веб-SDK. */
        fun putInto(json: JSONObject, withSegment: Boolean = true): JSONObject {
            json.put("shop_id", shopId).put("did", did).put("sid", sid)
            if (withSegment) json.put("segment", segment)
            if (stream.isNotEmpty()) json.put("stream", stream)
            if (testMode) json.put("testmode", true)
            return json
        }
    }

    /**
     * Поля запроса из SDK: `sid` — сеанс нового ядра (без него пусто), `segment` — A/B-группа
     * устройства (`segments.getAbGroup()`), `stream` — из конфигурации магазина, тестовый режим —
     * тот же, что у попапов v3 (`popups.testMode`). Зовётся в потоке запроса, после [whenDid].
     */
    @OptIn(BridgeApi::class)
    fun identity(sdk: SDK): Identity = Identity(
        shopId = shopId(sdk),
        did = sdk.getDid().orEmpty(),
        sid = runCatching { sdk.sessionId }.getOrNull().orEmpty(),
        segment = runCatching { sdk.segments.getAbGroup() }.getOrNull().orEmpty(),
        stream = runCatching { sdk.requestStream }.getOrNull().orEmpty(),
        testMode = runCatching { sdk.popupsV3?.testMode == true }.getOrDefault(false)
    )

    fun feedUrl(domain: String, code: String, identity: Identity): String =
        base(domain) + PATH + segment(code) + identity.query()

    fun storyUrl(domain: String, link: StoriesShare.Link, identity: Identity): String =
        base(domain) + PATH + segment(link.code) + "/story/" + link.storyId + identity.query() +
            (link.slideId?.let { "&slide_id=" + query(it) } ?: "")

    fun eventsUrl(domain: String, code: String): String = base(domain) + PATH + segment(code) + "/events"

    /** Тело события: `story_id` — число, как в ленте, `slide_id` — всегда строка (и `12_0`). */
    fun eventBody(identity: Identity, event: String, storyId: Int, slideId: String): JSONObject =
        identity.putInto(JSONObject())
            .put("event", event)
            .put("story_id", storyId)
            .put("slide_id", slideId)

    /**
     * Ответ GET: `{"status":"success","payload":{…}}` → сторис из `payload` тем же разбором,
     * что старая лента. JSON `null` (в v3 у товарных слайдов `background` и `preview`) убирается
     * до моделей: иначе `optString` отдал бы строку `"null"`. Слайды, которые веб-SDK пропускает, и
     * `start_position` остаются как пришли: их проходит просмотр ([StoriesViewerState]).
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
        load(sdk, { identity -> feedUrl(domain, code, identity) }, onLoaded, onFailed)

    fun story(sdk: SDK, domain: String, link: StoriesShare.Link, onLoaded: (List<Story>) -> Unit, onFailed: (Int, String?) -> Unit) =
        load(sdk, { identity -> storyUrl(domain, link, identity) }, onLoaded, onFailed)

    /** Событие сторис; источник трекинга ставится, как у старого `track/stories`. */
    fun track(sdk: SDK, domain: String, code: String, event: String, storyId: Int, slideId: String) {
        runCatching { sdk.storiesManager.setTrackingSourceUseCase(StoriesManager.STORIES_SOURCE_TYPE, code) }
        whenDid(sdk) {
            val body = eventBody(identity(sdk), event, storyId, slideId)
            // A failed answer is logged by the call itself, as request_failed.
            post(eventsUrl(domain, code), body, sdk.logShopId)
        }
    }

    /** Ответ — на главном потоке. HTTP-ошибка или `status != success` — [onFailed]. */
    private fun load(sdk: SDK, url: (Identity) -> String, onLoaded: (List<Story>) -> Unit, onFailed: (Int, String?) -> Unit) {
        whenDid(sdk) {
            val response = try {
                call("GET", url(identity(sdk)), null, sdk.logShopId)
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
     * она не выполнится. В новом ядре перед задачей — проверка окна сессии, как у любого запроса
     * ядра: если окно кончилось, сначала `/init`, и задача берёт живой `sid`.
     */
    fun whenDid(sdk: SDK, task: () -> Unit) {
        val once = AtomicBoolean(false)
        val run = Runnable {
            if (!once.compareAndSet(false, true)) return@Runnable
            try {
                sdk.awaitLiveSession()
                task()
            } catch (throwable: Throwable) {
                SdkLogs.log(LogLevel.ERROR, LogCodes.STORIES_V3, "stories v3: request failed", sdk.logShopId, throwable = throwable)
            }
        }
        try {
            if (sdk.getDid().isNullOrEmpty()) {
                sdk.addTaskToQueueUseCase.invoke(Thread(run))
                if (sdk.getDid().isNullOrEmpty()) return
            }
        } catch (throwable: Throwable) {
            SdkLogs.log(LogLevel.ERROR, LogCodes.STORIES_V3, "stories v3: SDK is not ready", sdk.logShopId, throwable = throwable)
        }
        runner(run)
    }

    fun shopId(sdk: SDK): String = sdk.getUserSettingsValueUseCase.getShopId()

    /**
     * POST с JSON-телом — события и опросы; зовётся не на главном потоке. [shopId] — магазин
     * экземпляра: строка лога проходит его уровень.
     */
    fun post(url: String, body: JSONObject, shopId: String? = null): Response = call("POST", url, body.toString(), shopId)

    /** GET — для других клиентов API v3 (попапы); зовётся не на главном потоке. */
    fun get(url: String, shopId: String? = null): Response = call("GET", url, null, shopId)

    /** Запрос с логом, как у сети SDK: строка в logcat и в `networkLogger`. */
    private fun call(method: String, url: String, body: String?, shopId: String?): Response {
        val started = System.currentTimeMillis()
        val response = try {
            http.send(method, url, body)
        } catch (throwable: Throwable) {
            SdkLogs.log(LogLevel.WARNING, LogCodes.REQUEST_FAILED, "$method ${pathOf(url)}", shopId, mapOf("method" to method, "path" to pathOf(url)), throwable)
            SDK.networkLogger?.onHttpCall(method, url, body, -1, null, System.currentTimeMillis() - started, throwable.message)
            throw throwable
        }
        // Method and path only: the query and the body carry did, the session and the user's answers.
        val success = response.code in 200..299
        SdkLogs.log(
            if (success) LogLevel.DEBUG else LogCodes.failureLevel(response.code),
            if (success) LogCodes.REQUEST else LogCodes.REQUEST_FAILED,
            "${response.code}: $method ${pathOf(url)}",
            shopId,
            mapOf("method" to method, "path" to pathOf(url), "status" to response.code)
        )
        SDK.networkLogger?.onHttpCall(method, url, body, response.code, response.body, System.currentTimeMillis() - started, null)
        return response
    }

    /** The path of [url] without the host and the query, for a log line: the query carries did and sid. */
    fun pathOf(url: String): String = runCatching { URL(url).path }.getOrDefault("")

    /**
     * The API v3 client of stories, polls and popups v3: the new core's pool, without cookies — it neither
     * sends one nor keeps what the server sets, whatever `java.net.CookieHandler` the app installed, as the
     * new core. Redirects are followed, as before.
     */
    private val client: OkHttpClient by lazy {
        CoreHttp.shared.newBuilder()
            .cookieJar(CookieJar.NO_COOKIES)
            .connectTimeout(CONNECT_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(READ_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .build()
    }

    private fun sendHttp(method: String, url: String, body: String?): Response {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", SDK.userAgent())
            .header("Accept", "application/json")
            .method(method, if (method == "GET" || method == "HEAD") null else body.orEmpty().toRequestBody(JSON))
            .build()
        client.newCall(request).execute().use { response ->
            return Response(response.code, response.body?.string().orEmpty())
        }
    }

    /** Часть пути: пробел — `%20`, не `+`. */
    fun segment(value: String): String = query(value).replace("+", "%20")

    private fun query(value: String): String = encode(value)

    /** Значение параметра запроса. */
    fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
