package com.personalization.core.internal

import android.content.SharedPreferences
import com.personalization.LogLevel
import com.personalization.PushProvider
import com.personalization.api.OnApiCallbackListener
import com.personalization.core.PushToken
import com.personalization.core.SdkError
import com.personalization.logs.LogCodes
import com.personalization.logs.LogReasons
import com.personalization.logs.SdkLogs
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

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
 *   hearing the first's answer. The claim is kept in the partition with its time, and the token and the
 *   date of the send only once the server took it. A failed send gives the claim back, a `2xx` answer
 *   `{"status": "error"}` among them (its listeners hear `decoding`), and a claim older
 *   than [CLAIM_TTL_MS] (five minutes) without a send — its process died — is free. The week, the claim and
 *   the send are kept for each provider (FCM, HMS) on its own.
 * - A token goes only once the instance is ready ([ready]) and never without a `did`. A token the app
 *   passes waits for the `did` and its listener hears the answer of the send: waiting is no error. After
 *   each start's `/init` ([sendStored]) the shop sends the device's token if it is due.
 * - A send begun before `deleteUserCredentials()` ([customerForgotten]) belongs to the forgotten customer: its
 *   answer still reaches its listeners, but records neither the token nor the week, and a send for the next
 *   `did` does not wait for it.
 * - A push-only instance ([sends] false) sends no token.
 * - After [release] nothing is sent or kept: [set] answers `notInitialized`, and so does one that was
 *   waiting for the instance to be ready.
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

    private val handler = CoroutineExceptionHandler { _, throwable ->
        log(LogLevel.ERROR, LogCodes.PUSH_TOKEN_FAILED, "The push token could not be sent", throwable = throwable)
    }

    /** The sends waiting for the instance; [release] cancels them. */
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io + handler)

    /** Answers that come from no running send: a refusal, a send [release] cut off. Never cancelled. */
    private val endings = CoroutineScope(SupervisorJob() + dispatchers.io + handler)

    private val delivery = LegacyDelivery { level, code, message, throwable -> log(level, code, message, throwable = throwable) }

    private val lock = Any()

    /** Guarded by [lock]: after [release] nothing is kept or sent. */
    private var released = false

    /** Guarded by [lock]: moves when the customer is forgotten; a send of an earlier one is not this customer's. */
    private var generation = 0

    /** The sends on their way, by `provider:token`, with the listeners waiting for their answer. */
    private val flights = HashMap<String, Flight>()

    /** A send on its way: when it took the week, for which customer ([generation]), and who waits for it. */
    private class Flight(val claimedAt: Long, val generation: Int) {
        val waiting = ArrayList<OnApiCallbackListener>()
    }

    /** Sends waiting for the instance and requests on their way; for [awaitIdle]. */
    private val busy = MutableStateFlow(0)

    /** The device's token of [provider], or the one this shop sent last. */
    fun token(provider: PushProvider): String? = device.pushToken(provider.id) ?: sentToken(provider)

    /**
     * `notifications.getPushToken`: the token of [provider] ([token]); without one, that of the provider
     * whose token this shop sent last, or — when it sent none — the device's, FCM before HMS. Null when there
     * is none, and after [release]. Reads the storage, never the network.
     */
    fun known(provider: PushProvider?): PushToken? {
        if (synchronized(lock) { released }) return null
        val providers = if (provider != null) {
            listOf(provider)
        } else {
            val sent = PushProvider.entries.filter { sentToken(it) != null }.sortedByDescending(::sentDate)
            sent + (PushProvider.entries - sent.toSet())
        }
        return providers.firstNotNullOfOrNull { candidate -> token(candidate)?.let { PushToken(it, candidate) } }
    }

    /**
     * A token FCM or HMS gave: kept and sent, when the shop takes them, once the instance is ready. A shop
     * that does not send tokens by itself neither keeps nor sends it (`push_token_skipped`, `auto_send_off`).
     */
    fun received(token: String, provider: PushProvider) {
        if (token.isEmpty()) return
        if (!autoSend) {
            skipped(provider, REASON_AUTO_SEND_OFF)
            return
        }
        synchronized(lock) {
            if (released) return
            device.savePushToken(provider.id, token)
        }
        launch { if (ready() == null) sendIfDue(token, provider, null) }
    }

    /**
     * A token the app passes (`setPushToken`): kept for the device, then sent by the same rules once the
     * instance is ready. [listener] hears the server's answer, success without a request when the token
     * went this week, or the error — `notInitialized` after [release], even while it waited for the
     * instance; then nothing is kept.
     */
    fun set(token: String, provider: PushProvider, listener: OnApiCallbackListener?) {
        if (token.isBlank()) {
            log(LogLevel.WARNING, LogCodes.INVALID_ARGUMENT, "setPushToken: the token is empty", mapOf("reason" to "empty_token"))
            refuse(listener, SdkError.InvalidArgument("The token is empty"))
            return
        }
        val kept = synchronized(lock) {
            if (!released) device.savePushToken(provider.id, token)
            !released
        }
        if (!kept) {
            refuse(listener, SdkError.NotInitialized)
            return
        }
        // Who answers [listener]: the send, or the release that cut it off before it went.
        val handed = AtomicBoolean(false)
        val job = launch {
            var error = ready()
            // Ready, yet the did went (the customer was deleted meanwhile): the token waits for the next one
            // and goes then; waiting for a did is no error.
            while (error == null && !hasDid()) {
                delay(DID_RECHECK_MS)
                error = ready()
            }
            if (!handed.compareAndSet(false, true)) return@launch
            if (error != null) {
                listener?.let { delivery.failure(it, LegacyErrors.of(error)) }
                return@launch
            }
            sendIfDue(token, provider, listener)
        }
        job.invokeOnCompletion { cause ->
            if (cause != null && handed.compareAndSet(false, true)) refuse(listener, SdkError.NotInitialized)
        }
    }

    /** After a start's `/init`: the device's tokens go if they are due. */
    fun sendStored() {
        PushProvider.entries.forEach { provider ->
            val token = device.pushToken(provider.id) ?: return@forEach
            launch { sendIfDue(token, provider, null) }
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
            skipped(provider, REASON_NO_DEVICE_ID)
            listener?.let { delivery.failure(it, LegacyErrors.of(SdkError.NotInitialized)) }
            return
        }
        val key = "${provider.id}:$token"
        val now = clock.nowMillis()
        val flight = synchronized(lock) {
            // The same token on its way for this customer: one request, this caller hears its answer. One
            // of a forgotten customer's does not count: the token goes again for this one.
            flights[key]?.takeIf { it.generation == generation }?.let { going ->
                listener?.let { going.waiting += it }
                return
            }
            val sentThisWeek = sentToken(provider) == token && now - sentDate(provider) in 0 until WEEK_MS
            // Taken by another process of the app moments ago; one whose process died is free.
            val claimed = claimedToken(provider) == token && now - claimedAt(provider) in 0 until CLAIM_TTL_MS
            if (sentThisWeek || claimed) {
                null
            } else {
                // The week is taken before the request: a second send meanwhile finds it taken.
                Flight(now, generation).also { taken ->
                    listener?.let { taken.waiting += it }
                    flights[key] = taken
                    busy.update { it + 1 }
                    shop.edit().putString(StorageKeys.pushTokenClaim(provider.id), token).putLong(StorageKeys.pushTokenClaimedAt(provider.id), now).apply()
                }
            }
        }
        if (flight == null) {
            skipped(provider, REASON_SENT_RECENTLY)
            listener?.let { delivery.success(it, JSONObject()) }
            return
        }
        val params = JSONObject()
            .put(FIELD_PLATFORM, PLATFORM_ANDROID)
            .put(FIELD_TOKEN, token)
            .put(FIELD_PUSH_PROVIDER, provider.serverValue)
        send(params, object : OnApiCallbackListener() {
            /** Guarded by [lock]: the answer was taken. */
            private var ended = false

            override fun onSuccess(response: JSONObject?) {
                if (response?.optString(FIELD_STATUS) == STATUS_ERROR) {
                    // A `2xx` whose body says it failed took no token: `decoding`, as the namespaces read
                    // such an answer, and the week stays free.
                    landed(failed = true) { waiting ->
                        log(LogLevel.WARNING, LogCodes.PUSH_TOKEN_FAILED, "Push token was not sent: the answer is an error with a 2xx status",
                            mapOf("provider" to provider.id, "error" to ERROR_DECODING))
                        waiting.forEach { delivery.failure(it, LegacyErrors.of(SdkError.Decoding)) }
                    }
                    return
                }
                landed { waiting ->
                    log(LogLevel.INFO, LogCodes.PUSH_TOKEN_SENT, "Push token sent", mapOf("provider" to provider.id))
                    waiting.forEach { delivery.success(it, response ?: JSONObject()) }
                }
            }

            override fun onSuccess(response: JSONArray) = landed { waiting ->
                log(LogLevel.INFO, LogCodes.PUSH_TOKEN_SENT, "Push token sent", mapOf("provider" to provider.id))
                waiting.forEach { delivery.success(it, response) }
            }

            override fun onError(code: Int, msg: String?) {
                // The whole error, while this runs on the funnel's thread.
                val error = CurrentError.get()
                landed(failed = true) { waiting ->
                    log(LogLevel.WARNING, LogCodes.PUSH_TOKEN_FAILED, "Push token was not sent", mapOf("provider" to provider.id, "status" to code))
                    waiting.forEach { delivery.failure(it, code, msg, error) }
                }
            }

            private fun landed(failed: Boolean = false, tell: (List<OnApiCallbackListener>) -> Unit) {
                val waiting = synchronized(lock) {
                    if (ended) return
                    ended = true
                    // A send of the next customer may have the key by now: it stays.
                    if (flights[key] === flight) flights.remove(key)
                    if (flight.generation == generation) settle(provider, token, flight, sent = !failed)
                    flight.waiting.toList()
                }
                try {
                    tell(waiting)
                } finally {
                    busy.update { it - 1 }
                }
            }
        })
    }

    /**
     * Must hold [lock]. The claim of [flight] ends: a send that went is recorded as the shop's, and the
     * claim goes unless another one wrote over it meanwhile.
     */
    private fun settle(provider: PushProvider, token: String, flight: Flight, sent: Boolean) {
        val editor = shop.edit()
        if (sent) {
            editor.putString(StorageKeys.pushToken(provider.id), token).putLong(StorageKeys.pushTokenDate(provider.id), flight.claimedAt)
        }
        if (claimedToken(provider) == token && claimedAt(provider) == flight.claimedAt) {
            editor.remove(StorageKeys.pushTokenClaim(provider.id)).remove(StorageKeys.pushTokenClaimedAt(provider.id))
        }
        editor.apply()
    }

    private fun claimedToken(provider: PushProvider): String? = shop.getString(StorageKeys.pushTokenClaim(provider.id), null)

    private fun claimedAt(provider: PushProvider): Long = shop.getLong(StorageKeys.pushTokenClaimedAt(provider.id), 0L)

    /** Runs [block] on the sends' scope, counted for [awaitIdle]. */
    private fun launch(block: suspend () -> Unit): Job {
        busy.update { it + 1 }
        val job = scope.launch { block() }
        job.invokeOnCompletion { busy.update { it - 1 } }
        return job
    }

    /** Hands [listener] [error] off the caller's thread. */
    private fun refuse(listener: OnApiCallbackListener?, error: SdkError) {
        listener ?: return
        endings.launch { delivery.failure(listener, LegacyErrors.of(error)) }
    }

    /** Returns once nothing waits for the instance and no request is on its way; for tests. */
    internal suspend fun awaitIdle() {
        busy.first { it == 0 }
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

    /**
     * The customer is about to be forgotten (`deleteUserCredentials()`), before their data is wiped: a send on
     * its way from now on records neither the token nor the week when it lands, and the next `did` sends the
     * token again without waiting for it.
     */
    fun customerForgotten() {
        synchronized(lock) { generation++ }
    }

    /**
     * Stops the sends waiting for the instance — a [set] among them hears `notInitialized` — and keeps or
     * sends nothing more; a request on its way still ends.
     */
    fun release() {
        synchronized(lock) { released = true }
        scope.cancel()
    }

    private fun log(level: LogLevel, code: String, message: String, context: Map<String, Any?> = emptyMap(), throwable: Throwable? = null) =
        SdkLogs.log(level, code, message, shopId, context, throwable)

    internal companion object {
        const val PATH = "mobile_push_tokens"
        const val WEEK_MS = 7 * 24 * 60 * 60 * 1000L

        /** A claim this old without a send is free: the process that took it died before the answer. */
        const val CLAIM_TTL_MS = 5 * 60 * 1000L

        /** How often a token the app passed looks again for the did a deletion of the customer took. */
        const val DID_RECHECK_MS = 100L

        /** The reasons of `push_token_skipped` (core/logging.md). */
        const val REASON_SENT_RECENTLY = LogReasons.SENT_RECENTLY
        const val REASON_NO_DEVICE_ID = LogReasons.NO_DEVICE_ID
        const val REASON_AUTO_SEND_OFF = LogReasons.AUTO_SEND_OFF
        const val REASON_PUSH_ONLY = LogReasons.PUSH_ONLY

        private const val FIELD_PLATFORM = "platform"
        private const val FIELD_STATUS = "status"
        private const val STATUS_ERROR = "error"
        private const val ERROR_DECODING = "decoding"
        private const val FIELD_TOKEN = "token"
        private const val FIELD_PUSH_PROVIDER = "push_provider"
        private const val PLATFORM_ANDROID = "android"
    }
}
