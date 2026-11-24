package com.personalization.ui.widgets

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import com.personalization.InternalPersonalizationV3Api
import com.personalization.LogLevel
import com.personalization.PreferencesPartition
import com.personalization.SDK
import com.personalization.logs.LogCodes
import com.personalization.logs.SdkLogs
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONArray
import org.json.JSONObject

/**
 * Транспорт опросов по умолчанию — HTTP на хост API v3, с которого пришла лента:
 * `https://{domain}/api/v3/polls/...`. Тело — JSON: `shop_id`, `did`, `sid` (всегда, как в веб-SDK:
 * без сеанса — пустая строка), `stream`, `testmode` в тестовом режиме и поля запроса; без cookies. Запрос, лог и разбор `{status, payload}` — общие
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
            SdkLogs.log(LogLevel.DEBUG, LogCodes.STORIES_V3, "stories poll: ${StoriesApiV3.pathOf(url)} failed: no did in ${DID_TIMEOUT_MS / 1000} s", sdk.logShopId)
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
            StoriesApiV3.post(url, json, sdk.logShopId)
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

    /** `shop_id`, `did`, `sid`, `stream` и `testmode` — как у ленты, но без `segment`, как в веб-SDK. */
    private fun identity(): JSONObject = StoriesApiV3.identity(sdk).putInto(JSONObject(), withSegment = false)

    // Path only, without the answer: the query carries did and sid, an error answer often repeats them.
    private fun failure(url: String, code: Int, message: String?): StoriesPollsResult {
        SdkLogs.log(LogLevel.DEBUG, LogCodes.STORIES_V3, "stories poll: ${StoriesApiV3.pathOf(url)} failed: $code", sdk.logShopId)
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
 * Ответы на опросы на устройстве, как `localStorage` веб-SDK: лента не знает ответов анонимных
 * покупателей, а слайд собирается из ленты при каждом показе. Ключ —
 * `stories_poll_{did}_{poll_type}_{code}_{id сторис}_{id слайда}`, значение — `{answer, results, saved_at}`
 * (мс). Записи старше [TTL_MS] (столько сервер помнит ответ) и без даты удаляются при чтении. Без did
 * ничего не читается и не пишется: чужой ответ не должен показаться. Свой файл SharedPreferences рядом
 * с файлом магазина — то же разбиение, что у SDK ([PreferencesPartition.keyFor]), как у увиденных слайдов.
 */
@OptIn(InternalPersonalizationV3Api::class)
internal class StoryPollAnswers internal constructor(
    private val preferences: SharedPreferences,
    private val now: () -> Long = System::currentTimeMillis
) {

    data class Entry(val answer: StoriesPollAnswer, val results: StoriesPollResults?)

    /** Ключ ответа; без [did] — `null`. */
    fun key(did: String?, pollType: String, code: String, storyId: Int, slideId: String): String? {
        if (did.isNullOrEmpty()) return null
        return listOf(PREFIX, did, pollType, code, storyId.toString(), slideId).joinToString("_")
    }

    /** Сохранённый ответ; нет — `null`; не разобрался, устарел или без даты — удаляется и `null`. */
    @Synchronized
    fun read(key: String?): Entry? {
        if (key == null) return null
        val raw = try {
            preferences.getString(key, null)
        } catch (_: ClassCastException) {
            ""
        } ?: return null
        val entry = try {
            val json = JSONObject(raw)
            val answer = json.optJSONObject("answer")
            val savedAt = json.opt("saved_at")
            if (answer == null || savedAt !is Number || now() - savedAt.toLong() > TTL_MS) null
            else Entry(StoryPoll.answer(answer), json.optJSONObject("results")?.let(StoryPoll::results))
        } catch (_: Exception) {
            null
        }
        if (entry == null) preferences.edit().remove(key).apply()
        return entry
    }

    @Synchronized
    fun write(key: String?, answer: StoriesPollAnswer, results: StoriesPollResults?) {
        if (key == null) return
        val json = JSONObject()
            .put("answer", StoryPoll.answerJson(answer))
            .put("results", results?.let(StoryPoll::resultsJson) ?: JSONObject.NULL)
            .put("saved_at", now())
        preferences.edit().putString(key, json.toString()).apply()
    }

    companion object {
        const val PREFIX = "stories_poll"

        /** 90 дней — столько лента помнит ответ (`STORIES_ANSWER_MEMORY_DAYS`). */
        const val TTL_MS = 90L * 24 * 60 * 60 * 1000

        private const val FILE_SUFFIX = "_stories_polls"
        private val stores = HashMap<String, StoryPollAnswers>()

        fun forShop(context: Context, shopKey: String): StoryPollAnswers = synchronized(stores) {
            stores.getOrPut(shopKey) {
                StoryPollAnswers(
                    context.applicationContext.getSharedPreferences(PreferencesPartition.keyFor(shopKey) + FILE_SUFFIX, Context.MODE_PRIVATE)
                )
            }
        }

        fun forSdk(context: Context, sdk: SDK, requestedShopId: String?): StoryPollAnswers =
            forShop(context, storageKey(sdk, requestedShopId))

        /** Для тестов: следующий [forShop] откроет файл заново. */
        internal fun reset() = synchronized(stores) { stores.clear() }
    }
}
