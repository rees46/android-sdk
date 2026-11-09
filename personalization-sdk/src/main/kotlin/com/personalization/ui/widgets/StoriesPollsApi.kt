package com.personalization.ui.widgets

import android.os.Handler
import android.os.Looper
import com.personalization.InternalPersonalizationV3Api
import com.personalization.SDK
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONArray
import org.json.JSONObject

/**
 * Транспорт опросов по умолчанию — HTTP на хост API v3, с которого пришла лента:
 * `https://{domain}/api/v3/polls/...`. Тело — JSON: `shop_id`, `did`, `sid` (всегда, как в веб-SDK:
 * без сеанса — пустая строка) и поля запроса; без cookies. Запрос, лог и разбор `{status, payload}` — общие
 * с [StoriesApiV3]; did ждётся там же, как у ленты. Не дождались за [DID_TIMEOUT_MS] — ошибка.
 */
@OptIn(InternalPersonalizationV3Api::class)
internal class StoriesPollsApi(private val sdk: SDK, private val domain: String) : StoriesPollsTransport {

    override fun answerMiniPoll(code: String, body: Map<String, Any>, callback: (StoriesPollsResult) -> Unit) =
        send(miniPollUrl(domain, code), body, callback)

    override fun review(kind: String, body: Map<String, Any>, callback: (StoriesPollsResult) -> Unit) =
        send(reviewUrl(domain, kind), body, callback)

    /** Ответ — на главном потоке, один раз: или запрос, или ошибка ожидания did. */
    private fun send(url: String, body: Map<String, Any>, callback: (StoriesPollsResult) -> Unit) {
        val claimed = AtomicBoolean(false)
        val timeout = Runnable {
            if (!claimed.compareAndSet(false, true)) return@Runnable
            SDK.debug("stories poll: $url failed: no did in ${DID_TIMEOUT_MS / 1000} s")
            callback(StoriesPollsResult.Failure(StoriesLoader.ERROR_CODE, "no did"))
        }
        handler.postDelayed(timeout, DID_TIMEOUT_MS)
        StoriesApiV3.whenDid(sdk) {
            if (!claimed.compareAndSet(false, true)) return@whenDid
            handler.removeCallbacks(timeout)
            val result = request(url, body)
            MainThread.run { callback(result) }
        }
    }

    /**
     * Синхронный запрос. Успех — HTTP 2xx (редиректы клиент проходит сам) с JSON без `status`
     * `fail|error`; остальное — ошибка, она пишется в debug-лог: карточка сама не логирует.
     */
    fun request(url: String, body: Map<String, Any>): StoriesPollsResult {
        val json = identity()
        body.forEach { (key, value) -> json.put(key, if (value is Collection<*>) JSONArray(value) else value) }
        val response = try {
            StoriesApiV3.post(url, json)
        } catch (throwable: Throwable) {
            return failure(url, StoriesLoader.ERROR_CODE, throwable.message)
        }
        if (response.code !in 200..299) return failure(url, response.code, message(response.body))
        val envelope = try {
            StoriesApiV3.envelope(response.body)
        } catch (throwable: Throwable) {
            return failure(url, StoriesLoader.ERROR_CODE, throwable.message)
        }
        val payload = envelope.optJSONObject("payload")
        return StoriesPollsResult.Success(
            answer = payload?.optJSONObject("answer")?.let(StoryPoll::answer),
            results = payload?.optJSONObject("results")?.let(StoryPoll::results)
        )
    }

    private fun identity(): JSONObject = JSONObject()
        .put("shop_id", StoriesApiV3.shopId(sdk))
        .put("did", sdk.getDid().orEmpty())
        .put("sid", runCatching { sdk.getSid() }.getOrNull().orEmpty())

    private fun failure(url: String, code: Int, message: String?): StoriesPollsResult {
        SDK.debug("stories poll: $url failed: $code $message")
        return StoriesPollsResult.Failure(code, message)
    }

    /** `message` ответа с ошибкой, иначе начало тела. */
    private fun message(body: String): String? =
        runCatching { JSONObject(body).optString("message") }.getOrNull()?.takeIf { it.isNotEmpty() }
            ?: body.take(MAX_MESSAGE).takeIf { it.isNotEmpty() }

    companion object {
        /** Сколько ждать did, как ленту v3. */
        const val DID_TIMEOUT_MS = 15_000L
        private const val MAX_MESSAGE = 200
        private val handler = Handler(Looper.getMainLooper())

        fun miniPollUrl(domain: String, code: String): String =
            StoriesApiV3.base(domain) + "/api/v3/polls/mini_polls/" + StoriesApiV3.segment(code) + "/answers"

        fun reviewUrl(domain: String, kind: String): String =
            StoriesApiV3.base(domain) + "/api/v3/polls/" + StoriesApiV3.segment(kind) + "/reviews"
    }
}

/**
 * Ответы на опросы в этой сессии — в памяти, одно на процесс: на все виджеты, [PersonalizationStories.show]
 * и [PersonalizationStories.handleLink]. Ключ — магазин, `poll_type` и код опроса. При сборке
 * карточки важнее ответа из ленты: лента может не успеть его узнать. На диск не пишется.
 */
@OptIn(InternalPersonalizationV3Api::class)
internal object StoryPollStore {

    data class Entry(val answer: StoriesPollAnswer?, val results: StoriesPollResults?)

    private val entries = HashMap<List<String>, Entry>()

    @Synchronized
    fun get(shopId: String, pollType: String, code: String): Entry? = entries[listOf(shopId, pollType, code)]

    @Synchronized
    fun put(shopId: String, pollType: String, code: String, entry: Entry) {
        entries[listOf(shopId, pollType, code)] = entry
    }

    /** Для тестов. */
    @Synchronized
    fun clear() = entries.clear()
}
