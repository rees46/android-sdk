package com.personalization.core.internal

import android.content.SharedPreferences
import com.personalization.LogLevel
import com.personalization.PushProvider
import com.personalization.api.OnApiCallbackListener
import com.personalization.core.SdkError
import com.personalization.logs.LogCodes
import com.personalization.logs.SdkLogs
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * The push token on the new core (`core/push.md`), for one shop.
 *
 * - The token is the device's: one per provider in [DeviceStorage], for every shop. A token from FCM or
 *   HMS is kept there when the shop sends tokens by itself (`autoSendPushToken`); one the app passes
 *   ([set]) always.
 * - Each shop sends it to its own server (`POST mobile_push_tokens` with `platform=android` and
 *   `push_provider` `firebase` or `huawei`) and keeps, in its partition, which token it sent last and
 *   when. A token the shop has not sent goes at once; the same token at most once a week.
 * - The week is taken before the request: two sends of one token at once make one request, the second
 *   hearing the first's answer. A failed send gives the week back.
 * - A token goes only once the instance is ready ([ready]) and never without a `did`. After each start's
 *   `/init` ([sendStored]) the shop sends the device's token if it is due.
 * - A push-only instance ([sends] false) sends no token.
 */
internal class CorePushTokens(
    private val shopId: String,
    private val device: DeviceStorage,
    private val shop: SharedPreferences,
    /** Sends the request's own fields through the instance's funnel. */
    private val send: (params: JSONObject, listener: OnApiCallbackListener) -> Unit,
    /** Waits for the instance to be ready: null when it is, otherwise why it never will be. */
    private val ready: suspend () -> SdkError?,
    private val hasDid: () -> Boolean,
    /** Whether the shop takes the tokens FCM and HMS give it (`Rees46Config.autoSendPushToken`). */
    private val autoSend: Boolean,
    private val sends: Boolean = true,
    dispatchers: CoreDispatchers = CoreDispatchers.DEFAULT,
    private val clock: Clock = Clock.SYSTEM,
) {

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io + CoroutineExceptionHandler { _, throwable ->
        log(LogLevel.ERROR, LogCodes.PUSH_TOKEN_FAILED, "The push token could not be sent", throwable = throwable)
    })

    private val delivery = LegacyDelivery { level, code, message, throwable -> log(level, code, message, throwable = throwable) }

    private val lock = Any()

    /** The sends on their way, by `provider:token`, with the listeners waiting for their answer. */
    private val flights = HashMap<String, Flight>()

    private class Flight(val claimedAt: Long, val previousToken: String?, val previousDate: Long) {
        val waiting = ArrayList<OnApiCallbackListener>()
    }

    /** The device's token of [provider], or the one this shop sent last. */
    fun token(provider: PushProvider): String? = device.pushToken(provider.id) ?: sentToken(provider)

    /** A token FCM or HMS gave: kept and sent, when the shop takes them, once the instance is ready. */
    fun received(token: String, provider: PushProvider) {
        if (token.isEmpty() || !autoSend) return
        device.savePushToken(provider.id, token)
        scope.launch { if (ready() == null) sendIfDue(token, provider, null) }
    }

    /**
     * A token the app passes (`setPushToken`): kept for the device, then sent by the same rules once the
     * instance is ready. [listener] hears the server's answer, success without a request when the token
     * went this week, or the error.
     */
    fun set(token: String, provider: PushProvider, listener: OnApiCallbackListener?) {
        if (token.isBlank()) {
            log(LogLevel.WARNING, LogCodes.INVALID_ARGUMENT, "setPushToken: the token is empty", mapOf("reason" to "empty_token"))
            listener?.let { delivery.failure(it, LegacyErrors.of(SdkError.InvalidArgument("The token is empty"))) }
            return
        }
        device.savePushToken(provider.id, token)
        scope.launch {
            val error = ready()
            if (error != null) {
                listener?.let { delivery.failure(it, LegacyErrors.of(error)) }
                return@launch
            }
            sendIfDue(token, provider, listener)
        }
    }

    /** After a start's `/init`: the device's tokens go if they are due. */
    fun sendStored() {
        PushProvider.entries.forEach { provider ->
            val token = device.pushToken(provider.id) ?: return@forEach
            scope.launch { sendIfDue(token, provider, null) }
        }
    }

    /** Sends [token] unless this shop sent it this week; [listener] hears how it ended. */
    internal fun sendIfDue(token: String, provider: PushProvider, listener: OnApiCallbackListener?) {
        if (!sends) {
            skipped(provider, REASON_PUSH_ONLY)
            listener?.let { delivery.success(it, JSONObject()) }
            return
        }
        if (!hasDid()) {
            // Not before /init gave a did: the next start's /init sends it.
            skipped(provider, REASON_NO_DID)
            listener?.let { delivery.failure(it, LegacyErrors.of(SdkError.NotInitialized)) }
            return
        }
        val key = "${provider.id}:$token"
        val now = clock.nowMillis()
        val flight = synchronized(lock) {
            flights[key]?.let { going ->
                // The same token on its way: one request, this caller hears its answer.
                listener?.let { going.waiting += it }
                return
            }
            val sentToken = sentToken(provider)
            val sentAt = sentDate(provider)
            if (sentToken == token && now - sentAt in 0 until WEEK_MS) {
                null
            } else {
                // The week is taken before the request: a second send meanwhile finds it taken.
                Flight(now, sentToken, sentAt).also { claimed ->
                    listener?.let { claimed.waiting += it }
                    flights[key] = claimed
                    shop.edit().putString(StorageKeys.pushToken(provider.id), token).putLong(StorageKeys.pushTokenDate(provider.id), now).apply()
                }
            }
        }
        if (flight == null) {
            skipped(provider, REASON_SENT_THIS_WEEK)
            listener?.let { delivery.success(it, JSONObject()) }
            return
        }
        val params = JSONObject()
            .put(FIELD_PLATFORM, PLATFORM_ANDROID)
            .put(FIELD_TOKEN, token)
            .put(FIELD_PUSH_PROVIDER, provider.serverValue)
        send(params, object : OnApiCallbackListener() {
            override fun onSuccess(response: JSONObject?) = landed { waiting ->
                log(LogLevel.INFO, LogCodes.PUSH_TOKEN_SENT, "Push token sent", mapOf("provider" to provider.id))
                waiting.forEach { delivery.success(it, response ?: JSONObject()) }
            }

            override fun onSuccess(response: JSONArray) = landed { waiting ->
                log(LogLevel.INFO, LogCodes.PUSH_TOKEN_SENT, "Push token sent", mapOf("provider" to provider.id))
                waiting.forEach { delivery.success(it, response) }
            }

            override fun onError(code: Int, msg: String?) {
                landed(failed = true) { waiting ->
                    log(LogLevel.WARNING, LogCodes.PUSH_TOKEN_FAILED, "Push token was not sent", mapOf("provider" to provider.id, "status" to code))
                    waiting.forEach { delivery.failure(it, code, msg) }
                }
            }

            private fun landed(failed: Boolean = false, tell: (List<OnApiCallbackListener>) -> Unit) {
                val waiting = synchronized(lock) {
                    val ended = flights.remove(key) ?: return
                    if (failed) giveBack(provider, token, ended)
                    ended.waiting.toList()
                }
                tell(waiting)
            }
        })
    }

    /** Must hold [lock]. A failed send gives the week back, unless something else wrote the claim over meanwhile. */
    private fun giveBack(provider: PushProvider, token: String, flight: Flight) {
        if (sentToken(provider) != token || sentDate(provider) != flight.claimedAt) return
        val editor = shop.edit()
        if (flight.previousToken == null) editor.remove(StorageKeys.pushToken(provider.id)) else editor.putString(StorageKeys.pushToken(provider.id), flight.previousToken)
        if (flight.previousDate == 0L) editor.remove(StorageKeys.pushTokenDate(provider.id)) else editor.putLong(StorageKeys.pushTokenDate(provider.id), flight.previousDate)
        editor.apply()
    }

    /** The token this shop sent last, under the key of this release or of the ones before. */
    private fun sentToken(provider: PushProvider): String? =
        shop.getString(StorageKeys.pushToken(provider.id), null)?.takeIf { it.isNotEmpty() }
            ?: StorageKeys.LEGACY_PUSH_TOKEN[provider.id]?.let { shop.getString(it, null) }?.takeIf { it.isNotEmpty() }

    /** When this shop sent its token last; 0 when it never did or the customer was deleted since. */
    private fun sentDate(provider: PushProvider): Long {
        val date = shop.getLong(StorageKeys.pushTokenDate(provider.id), 0L)
        if (date != 0L) return date
        return StorageKeys.LEGACY_PUSH_TOKEN_DATE[provider.id]?.let { shop.getLong(it, 0L) } ?: 0L
    }

    private fun skipped(provider: PushProvider, reason: String) =
        log(LogLevel.DEBUG, LogCodes.PUSH_TOKEN_SKIPPED, "Push token not sent", mapOf("provider" to provider.id, "reason" to reason))

    /** Stops the sends waiting for the instance; a request on its way still ends. */
    fun release() = scope.cancel()

    private fun log(level: LogLevel, code: String, message: String, context: Map<String, Any?> = emptyMap(), throwable: Throwable? = null) =
        SdkLogs.log(level, code, message, shopId, context, throwable)

    internal companion object {
        const val PATH = "mobile_push_tokens"
        const val WEEK_MS = 7 * 24 * 60 * 60 * 1000L

        const val REASON_SENT_THIS_WEEK = "sent_this_week"
        const val REASON_NO_DID = "no_did"
        const val REASON_PUSH_ONLY = "push_only"

        private const val FIELD_PLATFORM = "platform"
        private const val FIELD_TOKEN = "token"
        private const val FIELD_PUSH_PROVIDER = "push_provider"
        private const val PLATFORM_ANDROID = "android"
    }
}
