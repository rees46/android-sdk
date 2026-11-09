package com.personalization.popups

import android.content.Context
import android.content.SharedPreferences
import com.personalization.PreferencesPartition
import com.personalization.SDK
import com.personalization.api.OnApiCallbackListener
import com.personalization.ui.widgets.MainThread
import com.personalization.ui.widgets.StoriesApiV3
import java.net.URLEncoder
import org.json.JSONObject

/**
 * Сеть попапов v3: список, один попап и события — на хосте ЛК (`appHost`,
 * `https://{appHost}/api/v3/storefront/popups…`) через общий клиент API v3 (тот же, что
 * у сторис и опросов: лог, `networkLogger`, ожидание did); промокод — на API-хосте SDK
 * (`promo_codes/fetch`), обычной сетью SDK.
 */
internal class PopupsV3Api(
    private val sdk: SDK,
    private val appHost: () -> String
) : PopupsV3Transport {

    @Volatile
    private var eventsPausedUntil = 0L

    override fun list(testMode: Boolean, debug: Boolean, callback: (PopupsV3ListResult) -> Unit) {
        StoriesApiV3.whenDid(sdk) {
            val url = listUrl(appHost(), shopId(), did(), sid(), testMode, debug)
            val result = try {
                val response = StoriesApiV3.get(url)
                when (response.code) {
                    in 200..299 -> try {
                        PopupsV3ListResult.Success(PopupsV3List.parse(response.body))
                    } catch (throwable: Throwable) {
                        // Ответ не по контракту — как у web: пустой список на срок по умолчанию.
                        PopupsV3ListResult.ClientError(response.code, throwable.message)
                    }
                    in 400..499 -> PopupsV3ListResult.ClientError(response.code, message(response.body))
                    else -> PopupsV3ListResult.Failure("${response.code} ${message(response.body)}")
                }
            } catch (throwable: Throwable) {
                PopupsV3ListResult.Failure(throwable.message)
            }
            MainThread.run { callback(result) }
        }
    }

    override fun one(id: Int, testMode: Boolean, callback: (PopupV3?) -> Unit) {
        StoriesApiV3.whenDid(sdk) {
            val popup = try {
                val response = StoriesApiV3.get(oneUrl(appHost(), id, shopId(), did(), sid(), testMode))
                if (response.code in 200..299) {
                    when (val parsed = PopupsV3List.parseOne(response.body)) {
                        is PopupV3.Parsed.Ok -> parsed.popup
                        is PopupV3.Parsed.Skipped -> {
                            SDK.debug("popups v3: popup $id skipped: ${parsed.reason}")
                            null
                        }
                        null -> null
                    }
                } else {
                    SDK.debug("popups v3: popup $id request failed: ${response.code} ${message(response.body)}")
                    null
                }
            } catch (throwable: Throwable) {
                SDK.debug("popups v3: popup $id request failed: ${throwable.message}")
                null
            }
            MainThread.run { callback(popup) }
        }
    }

    /**
     * Событие: один повтор при сбое сети или 5xx, 4xx не повторяется; 429 — пауза отправки
     * на минуту. Своего дедупа нет — 5-секундный делает сервер.
     */
    override fun event(id: Int, event: String, channel: String?) {
        if (System.currentTimeMillis() < eventsPausedUntil) {
            SDK.debug("popups v3: event $event of popup $id dropped: too many requests")
            return
        }
        StoriesApiV3.whenDid(sdk) {
            val url = eventsUrl(appHost(), id)
            val body = eventBody(shopId(), did(), sid(), event, channel)
            for (attempt in 0..1) {
                val code = try {
                    StoriesApiV3.post(url, body).code
                } catch (throwable: Throwable) {
                    -1
                }
                when {
                    code in 200..299 -> return@whenDid
                    code == 429 -> {
                        eventsPausedUntil = System.currentTimeMillis() + EVENTS_PAUSE_MS
                        return@whenDid
                    }
                    code in 400..499 -> return@whenDid
                }
                if (attempt == 0 && retryDelayMs > 0) Thread.sleep(retryDelayMs)
            }
        }
    }

    override fun promoCode(listId: String, callback: (String?) -> Unit) {
        sdk.sendNetworkMethodUseCase.getAsync(
            PROMO_METHOD,
            JSONObject().put("id", listId),
            object : OnApiCallbackListener() {
                override fun onSuccess(response: JSONObject?) {
                    val code = response?.let { PopupV3Values.present(it.opt("code")) }
                    MainThread.run { callback(code) }
                }

                override fun onError(code: Int, msg: String?) {
                    SDK.debug("popups v3: promo code of list $listId failed: $code $msg")
                    MainThread.run { callback(null) }
                }
            }
        )
    }

    private fun shopId(): String = StoriesApiV3.shopId(sdk)
    private fun did(): String = sdk.getDid().orEmpty()
    private fun sid(): String = runCatching { sdk.getSid() }.getOrNull().orEmpty()

    private fun message(body: String): String? =
        runCatching { JSONObject(body).optString("message") }.getOrNull()?.takeIf { it.isNotEmpty() }
            ?: body.take(MAX_MESSAGE).takeIf { it.isNotEmpty() }

    companion object {
        private const val PATH = "/api/v3/storefront/popups"
        const val PROMO_METHOD = "promo_codes/fetch"
        const val EVENTS_PAUSE_MS = 60_000L
        private const val MAX_MESSAGE = 200

        /** Пауза перед повтором события; в тестах — 0. */
        @Volatile
        var retryDelayMs = 1_000L

        fun listUrl(host: String, shopId: String, did: String, sid: String, testMode: Boolean, debug: Boolean): String =
            StoriesApiV3.base(host) + PATH + identity(shopId, did, sid) + "&channel=mobile" +
                (if (testMode) "&testmode=true" else "") + (if (debug) "&debug=1" else "")

        fun oneUrl(host: String, id: Int, shopId: String, did: String, sid: String, testMode: Boolean): String =
            StoriesApiV3.base(host) + PATH + "/" + id + identity(shopId, did, sid) + (if (testMode) "&testmode=true" else "")

        fun eventsUrl(host: String, id: Int): String = StoriesApiV3.base(host) + PATH + "/" + id + "/events"

        /** Тело события: `channel` — только у `subscribe` (канал подписки). */
        fun eventBody(shopId: String, did: String, sid: String, event: String, channel: String?): JSONObject =
            JSONObject()
                .put("shop_id", shopId)
                .put("did", did)
                .put("sid", sid)
                .put("event", event)
                .apply { if (!channel.isNullOrEmpty()) put("channel", channel) }

        private fun identity(shopId: String, did: String, sid: String): String =
            "?shop_id=" + query(shopId) + "&did=" + query(did) + "&sid=" + query(sid)

        private fun query(value: String): String = URLEncoder.encode(value, "UTF-8")
    }
}

/** Частота показов на диске: свой файл в разделе магазина, как увиденные сторис. */
internal class PopupsV3Preferences(private val preferences: SharedPreferences) : PopupsV3Storage {

    override fun getLong(key: String): Long? = if (preferences.contains(key)) preferences.getLong(key, 0L) else null

    override fun putLong(key: String, value: Long) {
        preferences.edit().putLong(key, value).apply()
    }

    override fun getString(key: String): String? = preferences.getString(key, null)

    override fun putString(key: String, value: String) {
        preferences.edit().putString(key, value).apply()
    }

    companion object {
        private const val FILE_SUFFIX = "_popups_v3"

        fun forShop(context: Context, shopId: String): PopupsV3Preferences = PopupsV3Preferences(
            context.applicationContext.getSharedPreferences(PreferencesPartition.keyFor(shopId) + FILE_SUFFIX, Context.MODE_PRIVATE)
        )
    }
}
