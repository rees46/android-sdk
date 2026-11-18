package com.personalization.features.notifications

import com.google.gson.Gson
import com.personalization.PushProvider
import com.personalization.SDK
import com.personalization.api.OnApiCallbackListener
import com.personalization.api.managers.NotificationsApi
import com.personalization.api.responses.notifications.GetAllNotificationsResponse
import com.personalization.core.BridgeApi
import com.personalization.core.BridgeErrors
import com.personalization.core.CompletionListener
import com.personalization.core.PushCallbacks
import com.personalization.core.PushEvent
import com.personalization.core.PushToken
import com.personalization.core.SdkError
import com.personalization.core.SdkException
import com.personalization.core.SdkResult
import com.personalization.core.internal.CorePushTokens
import com.personalization.core.internal.NamespaceCalls
import com.personalization.core.internal.invalidArgument
import com.personalization.core.internal.isCalendarDate
import com.personalization.core.internal.putCustomer
import com.personalization.core.internal.putIfNotNull
import com.personalization.core.internal.requireAtLeast
import com.personalization.core.internal.requireContact
import com.personalization.core.internal.requireNotBlank
import com.personalization.core.internal.secretRequest
import com.personalization.sdk.domain.models.NetworkMethod
import kotlinx.coroutines.CompletableDeferred
import org.json.JSONArray
import org.json.JSONObject

/**
 * [NotificationsApi] over the instance's push machinery: its queue for the push events
 * ([SDK.trackPushEvent], once per message in a process), its [CorePushTokens] for the token, its push
 * callbacks. Each call first passes [NamespaceCalls.checked]: the legacy core refuses it, and an empty code
 * or type of a push event is `invalidArgument`.
 */
@OptIn(BridgeApi::class)
internal class NotificationsApiImpl(private val sdk: SDK, private val calls: NamespaceCalls) : NotificationsApi {

    override fun trackDelivered(code: String, type: String, listener: CompletionListener<Unit>?) =
        track(PushEvent.DELIVERED, TRACK_DELIVERED, code, type, listener)

    override fun trackOpened(code: String, type: String, listener: CompletionListener<Unit>?) =
        track(PushEvent.OPENED, TRACK_OPENED, code, type, listener)

    override fun trackClicked(code: String, type: String, listener: CompletionListener<Unit>?) =
        track(PushEvent.CLICKED, TRACK_CLICKED, code, type, listener)

    override fun trackClosed(code: String, type: String, listener: CompletionListener<Unit>?) =
        track(PushEvent.CLOSED, TRACK_CLOSED, code, type, listener)

    override fun getPushToken(provider: PushProvider?): PushToken? {
        if (!onNewCore(GET_PUSH_TOKEN)) return null
        return tokens()?.known(provider)
    }

    override suspend fun setPushToken(token: String, provider: PushProvider) {
        calls.checked(SET_PUSH_TOKEN) {}
        val tokens = tokens() ?: throw SdkException(SdkError.NotInitialized)
        val ended = CompletableDeferred<SdkError?>()
        // An empty token is refused there, with its log line; one that went this week ends in success, and a
        // `2xx` answer `{"status": "error"}` in `decoding`, the week left free.
        tokens.set(token, provider, object : OnApiCallbackListener() {
            override fun onSuccess(response: JSONObject?) {
                ended.complete(null)
            }

            override fun onSuccess(response: JSONArray) {
                ended.complete(null)
            }

            override fun onError(code: Int, msg: String?) {
                // The whole error, while it is this thread's.
                ended.complete(BridgeErrors.errorOf(code, msg))
            }
        })
        ended.await()?.let { throw SdkException(it) }
    }

    override fun setPushToken(token: String, provider: PushProvider, listener: CompletionListener<Unit>) =
        calls.complete(listener) { setPushToken(token, provider) }

    override suspend fun getHistory(
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
    ): GetAllNotificationsResponse {
        val params = calls.checked(GET_HISTORY) {
            val request = secretRequest(shopSecret).put(HISTORY_TYPE, requireNotBlank(type, "type"))
            request.putCustomer(
                Triple("phone", "phone", phone),
                Triple("email", "email", email),
                Triple("externalId", "external_id", externalId),
                Triple("loyaltyId", "loyalty_id", loyaltyId),
                Triple("did", "did", did),
            )
            if (dateFrom == null || !isCalendarDate(dateFrom)) invalidArgument("dateFrom must be a date YYYY-MM-DD")
            requireContact(channel, "channel")
            requireAtLeast(limit, 1, "limit")
            requireAtLeast(page, 1, "page")
            request.put(HISTORY_DATE_FROM, dateFrom)
                .putIfNotNull(HISTORY_CHANNEL, channel)
                .putIfNotNull(HISTORY_PAGE, page)
                .putIfNotNull(HISTORY_LIMIT, limit)
        }
        return calls.request(GET_HISTORY, NetworkMethod.GET(HISTORY_PATH), params, secret = true, read = ::readHistory)
    }

    override fun getHistory(
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
    ) = calls.complete(listener) { getHistory(shopSecret, type, phone, email, externalId, loyaltyId, channel, limit, page, dateFrom, did) }

    override fun setCallbacks(callbacks: PushCallbacks) {
        if (onNewCore(SET_CALLBACKS)) sdk.setPushCallbacks(callbacks)
    }

    /**
     * Hands the push [event] to the instance's queue on the caller's thread, so events keep the order they
     * were reported in; [listener] hears how it ended on the main thread.
     */
    private fun track(event: PushEvent, call: String, code: String, type: String, listener: CompletionListener<Unit>?) {
        val empty = when {
            code.isBlank() -> FIELD_CODE
            type.isBlank() -> FIELD_TYPE
            else -> null
        }
        try {
            calls.checked(call, reason = empty?.let { "empty_$it" }) { empty?.let { invalidArgument("$it is empty") } }
        } catch (e: SdkException) {
            calls.tell(listener, SdkResult.Failure(e.error))
            return
        }
        val ended = CompletableDeferred<SdkError?>()
        sdk.trackPushEvent(event, code, type) { ended.complete(it) }
        listener?.let { calls.complete(it) { ended.await()?.let { error -> throw SdkException(error) } } }
    }

    /** Whether [call] may run: the new core; otherwise `notInitialized`, logged on the legacy core. */
    private fun onNewCore(call: String): Boolean = try {
        calls.checked(call) {}
        true
    } catch (e: SdkException) {
        false
    }

    /** The instance's token rules; set on the new core with its start. */
    private fun tokens(): CorePushTokens? = try {
        sdk.pushTokenManager.core
    } catch (e: UninitializedPropertyAccessException) {
        null
    }

    internal companion object {
        const val TRACK_DELIVERED = "notifications.trackDelivered"
        const val TRACK_OPENED = "notifications.trackOpened"
        const val TRACK_CLICKED = "notifications.trackClicked"
        const val TRACK_CLOSED = "notifications.trackClosed"
        const val GET_PUSH_TOKEN = "notifications.getPushToken"
        const val SET_PUSH_TOKEN = "notifications.setPushToken"
        const val SET_CALLBACKS = "notifications.setCallbacks"
        const val GET_HISTORY = "notifications.getHistory"
        const val HISTORY_PATH = "notifications"

        // The fields the root method sends (`GetAllNotificationsParams`).
        private const val HISTORY_TYPE = "type"
        private const val HISTORY_CHANNEL = "channel"
        private const val HISTORY_DATE_FROM = "date_from"
        private const val HISTORY_PAGE = "page"
        private const val HISTORY_LIMIT = "limit"

        /**
         * A history answer, `{"status": "success", "payload": {"messages": [...]}}`, read into the released
         * [GetAllNotificationsResponse] as the root method reads it; null (`decoding`) without its messages.
         */
        fun readHistory(body: Any?): GetAllNotificationsResponse? {
            val json = body as? JSONObject ?: return null
            if (json.optJSONObject("payload")?.optJSONArray("messages") == null) return null
            return Gson().fromJson(json.toString(), GetAllNotificationsResponse::class.java)
        }

        private const val FIELD_CODE = "code"
        private const val FIELD_TYPE = "type"
    }
}
