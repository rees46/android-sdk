package com.personalization.api.managers

import com.personalization.PushProvider
import com.personalization.core.BridgeApi
import com.personalization.core.CompletionListener
import com.personalization.core.PushCallbacks
import com.personalization.core.PushToken
import com.personalization.core.SdkError
import com.personalization.core.SdkException

/**
 * Mobile pushes of one shop — the `notifications` namespace: the device's push token, the push events an
 * app that parses its pushes itself reports (`delivered`, `opened`, `clicked`, `closed`, decision 0004) and
 * the push callbacks. A push the SDK sees itself goes through `Rees46.handlePush`, which finds its shop.
 *
 * The push events go through the instance's queue, as the tracking events do: a call ends once its event is
 * accepted — written to disk — not once the server has it, and the SDK adds `shop_id`, `did`, `stream` and
 * `segment` itself, never `sid`. One event of one message (its `code`) goes once per process: a repeat sends
 * nothing and ends in success.
 *
 * For the Flutter and React Native bridges: it runs on the new core only (`Rees46.initializeBridge`). On an
 * instance of the legacy core a call sends nothing, a call with a result fails with
 * [SdkError.NotInitialized], and the log says why (`core_flag_mismatch`).
 */
@BridgeApi
interface NotificationsApi {

    /**
     * The push of message [code] (the push's `id`) of mailing [type] (`bulk`, `chain`, `transactional`,
     * `digest`) reached the app: `POST track/delivered` through the queue. [listener] hears, once, on the
     * main thread, success once the event is accepted or was accepted before, or a failure with
     * [SdkError.InvalidArgument] for an empty [code] or [type], [SdkError.NotInitialized] or
     * [SdkError.ShopBlocked]; never a network error: the event waits in the queue. The push callbacks do not
     * hear of it: the app knows of it already.
     */
    fun trackDelivered(code: String, type: String, listener: CompletionListener<Unit>? = null)

    /** The notification of the push was shown: `POST track/opened`; otherwise as [trackDelivered]. */
    fun trackOpened(code: String, type: String, listener: CompletionListener<Unit>? = null)

    /**
     * The notification of the push was tapped: `POST track/clicked`; otherwise as [trackDelivered]. A [type]
     * of `TrackingSourceType` also sets the tracking source for 48 hours, as `tracking.setSource` does: the
     * `/push` events carry it as `source`.
     */
    fun trackClicked(code: String, type: String, listener: CompletionListener<Unit>? = null)

    /** The notification of the push was dismissed without a tap: `POST track/closed`; otherwise as [trackDelivered]. */
    fun trackClosed(code: String, type: String, listener: CompletionListener<Unit>? = null)

    /**
     * The push token the SDK knows for this shop, read from the storage without the network: [provider]'s;
     * without one, that of the provider whose token the shop sent last, or the device's when it sent none,
     * FCM before HMS. Null when there is none, on the legacy core, and after `release()`.
     */
    fun getPushToken(provider: PushProvider? = null): PushToken?

    /**
     * A [token] the app got from [provider] itself (its own messaging service, or `autoSendPushToken =
     * false`): kept for the device and sent to this shop's server, `POST mobile_push_tokens` with
     * `platform=android` and `push_provider` `firebase` or `huawei`. A token the shop sent this week ends in
     * success without a request; a new one goes at once. The send waits for the instance to be ready — it
     * needs the `did` — without a limit of its own: cancelling the call stops the wait for its answer, not
     * the send.
     *
     * @throws SdkException with [SdkError.InvalidArgument] (an empty [token]), [SdkError.NotInitialized] (the
     * legacy core, an instance not initialized or released), [SdkError.ShopBlocked], [SdkError.Network],
     * [SdkError.Timeout], [SdkError.Http] (`400` without a token or a client, `403` for an unknown or
     * disabled shop, `404` without `shop_id`) or [SdkError.Decoding] (a `2xx` answer with `"status": "error"`).
     */
    suspend fun setPushToken(token: String, provider: PushProvider)

    /** [setPushToken] for Java and for code outside coroutines: [listener] gets its result once, on the main thread. */
    fun setPushToken(token: String, provider: PushProvider, listener: CompletionListener<Unit>)

    /**
     * This shop's push callbacks, as `SDK.setPushCallbacks` sets them: a new call replaces the ones before,
     * `PushCallbacks()` clears them. Nothing on the legacy core.
     */
    fun setCallbacks(callbacks: PushCallbacks)
}
