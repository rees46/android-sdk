package com.personalization.api.managers

import com.personalization.PushProvider
import com.personalization.api.responses.notifications.GetAllNotificationsResponse
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
 * [getHistory] is a server method: it asks the history of a customer's messages with the shop's secret key.
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

    /**
     * The messages the shop sent the customer (`GET /notifications`): mailings of [type] (`bulk`, `chain` or
     * `trigger`, `transactional`, several joined with commas) on [channel] (`email`, `mobile_push`, `web_push`,
     * `sms` and the others, several joined with commas; without one the server answers an empty list), sent
     * since [dateFrom] (`YYYY-MM-DD`, which the server needs). [limit] and [page] go when given. The released
     * [GetAllNotificationsResponse].
     *
     * A server method: it takes the shop's secret key, [shopSecret], which the caller passes and the SDK
     * neither keeps nor writes to the log or hands to the app's network logger. A secret in an app's binary
     * gives whoever unpacks it the shop's server access — whether such methods stay in the SDK is decided in
     * DEV-4723. The request carries `shop_id` and none of the device's fields, so the customer is the one of
     * [phone], [email], [externalId], [loyaltyId] or [did], whichever are given: [did] goes only when passed
     * (the device's own is `SDK.getDid()`).
     *
     * @throws SdkException with [SdkError.InvalidArgument] for an empty [shopSecret] or [type], an empty id,
     * no id at all, a [dateFrom] that is missing or no date `YYYY-MM-DD`, or a [limit] or [page] below 1, before
     * any request; [SdkError.Http] `400` for a wrong secret, `404` for a customer the server does not find;
     * [SdkError.NotInitialized] (the legacy core, an instance not initialized or released),
     * [SdkError.ShopBlocked], [SdkError.Network], [SdkError.Timeout], [SdkError.Decoding] or
     * [SdkError.Cancelled] (`release()`).
     */
    suspend fun getHistory(
        shopSecret: String,
        type: String,
        phone: String? = null,
        email: String? = null,
        externalId: String? = null,
        loyaltyId: String? = null,
        channel: String? = null,
        limit: Int? = null,
        page: Int? = null,
        dateFrom: String? = null,
        did: String? = null,
    ): GetAllNotificationsResponse

    /** [getHistory] for Java and for code outside coroutines: [listener] gets its result once, on the main thread. */
    fun getHistory(
        shopSecret: String,
        type: String,
        phone: String?,
        email: String?,
        externalId: String?,
        loyaltyId: String?,
        channel: String?,
        limit: Int?,
        page: Int?,
        dateFrom: String?,
        did: String?,
        listener: CompletionListener<GetAllNotificationsResponse>,
    )
}
