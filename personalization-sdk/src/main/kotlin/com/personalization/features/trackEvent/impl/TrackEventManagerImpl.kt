package com.personalization.features.trackEvent.impl

import com.personalization.logs.SdkLogs
import com.personalization.logs.LogCodes
import com.personalization.LogLevel
import com.personalization.SDK
import com.personalization.SdkRegistry
import com.personalization.Params
import com.personalization.Params.TrackEvent
import com.personalization.api.OnApiCallbackListener
import com.personalization.api.managers.InAppNotificationManager
import com.personalization.api.managers.TrackEventManager
import com.personalization.api.models.purchase.PurchaseTrackingRequest
import com.personalization.api.params.ProductItemParams
import com.personalization.core.internal.ArgumentRefusals
import com.personalization.core.internal.EventAnswerListener
import com.personalization.sdk.data.mappers.popup.PopupDtoMapper
import com.personalization.sdk.data.models.params.SdkInitializationParams.PARAM_POPUP
import com.personalization.sdk.domain.usecases.network.SendNetworkMethodUseCase
import com.personalization.sdk.domain.usecases.recommendation.GetRecommendedByUseCase
import com.personalization.sdk.domain.usecases.recommendation.SetRecommendedByUseCase
import com.personalization.sdk.domain.usecases.trackingSource.GetTrackingSourceUseCase
import com.personalization.sdk.domain.usecases.userSettings.GetUserSettingsValueUseCase
import com.personalization.sdk.data.models.params.UserBasicParams
import org.json.JSONException
import org.json.JSONObject
import javax.inject.Inject

internal class TrackEventManagerImpl @Inject constructor(
    val getRecommendedByUseCase: GetRecommendedByUseCase,
    val setRecommendedByUseCase: SetRecommendedByUseCase,
    private val sendNetworkMethodUseCase: SendNetworkMethodUseCase,
    private val inAppNotificationManager: InAppNotificationManager,
    private val getUserSettingsValueUseCase: GetUserSettingsValueUseCase,
    private val getTrackingSourceUseCase: GetTrackingSourceUseCase
) : TrackEventManager {

    override fun track(event: TrackEvent, productId: String) {
        track(event, Params().put(ProductItemParams(productId)), null)
    }

    override fun track(
        event: TrackEvent,
        params: Params,
        listener: OnApiCallbackListener?
    ) {
        params.put(EVENT_PARAMETER, event.value)
        if (event == TrackEvent.PURCHASE) popups()?.onPurchase()
        val lastRecommendedBy = getRecommendedByUseCase()
        if (lastRecommendedBy != null) {
            params.put(lastRecommendedBy)
            setRecommendedByUseCase(null)
        }

        val internalListener = answerListener(listener)

        val body = params.build()
        attachStoredSource(body)

        sendNetworkMethodUseCase.postAsync(
            PUSH_REQUEST,
            body,
            internalListener
        )
    }

    override fun trackPurchase(request: PurchaseTrackingRequest, listener: OnApiCallbackListener?) {
        val body = PurchaseTrackingJsonBuilder.buildOrError(request).getOrElse { error ->
            refuse(listener, PurchaseTrackingJsonBuilder.CLIENT_VALIDATION_ERROR_CODE, error.message)
            return
        }
        popups()?.onPurchase()
        if (request.recommendedBy == null) {
            val lastRecommendedBy = getRecommendedByUseCase()
            if (lastRecommendedBy != null) {
                PurchaseTrackingJsonBuilder.mergeInto(body, Params().put(lastRecommendedBy).build())
                setRecommendedByUseCase(null)
            }
        }

        val internalListener = answerListener(listener)

        attachStoredSource(body)

        sendNetworkMethodUseCase.postAsync(
            PUSH_REQUEST,
            body,
            internalListener,
        )
    }

    override fun trackEvent(
        event: String,
        time: Int?,
        category: String?,
        label: String?,
        value: Int?,
        customFields: Map<String, Any?>?,
        listener: OnApiCallbackListener?
    ) {
        val effectiveCustom = TrackCustomEventPayloadHelper.effectiveCustomFields(customFields)
        TrackCustomEventPayloadHelper.validateNoReservedKeyCollisions(effectiveCustom)?.let { msg ->
            refuse(listener, TrackCustomEventPayloadHelper.CLIENT_VALIDATION_ERROR_CODE, msg)
            return
        }

        popups()?.onTrackEvent(event)
        val params = Params()
        params.put(EVENT_PARAMETER, event)
        if (time != null) {
            params.put(TIME_PARAMETER, time)
        }
        if (category != null) {
            params.put(CATEGORY_PARAMETER, category)
        }
        if (label != null) {
            params.put(LABEL_PARAMETER, label)
        }
        if (value != null) {
            params.put(VALUE_PARAMETER, value)
        }

        val body = params.build()
        if (effectiveCustom.isNotEmpty()) {
            try {
                val payload = JSONObject()
                for ((key, fieldValue) in effectiveCustom) {
                    TrackCustomEventPayloadHelper.putJsonValue(body, key, fieldValue)
                    TrackCustomEventPayloadHelper.putJsonValue(payload, key, fieldValue)
                }
                body.put(PAYLOAD_PARAMETER, payload)
            } catch (e: JSONException) {
                refuse(
                    listener,
                    TrackCustomEventPayloadHelper.CLIENT_VALIDATION_ERROR_CODE,
                    "trackEvent: failed to build custom fields payload: ${e.message}"
                )
                return
            }
        }

        postCustomEvent(body, listener)
    }

    @Deprecated("Use trackEvent for iOS-aligned custom events.")
    override fun customTrack(
        event: String,
        email: String?,
        phone: String?,
        loyaltyId: String?,
        externalId: String?,
        category: String?,
        label: String?,
        value: Int?,
        listener: OnApiCallbackListener?
    ) {
        popups()?.onTrackEvent(event)
        val params = Params()
        params.put(EVENT_PARAMETER, event)
        if (email != null) {
            params.put(EMAIL_PARAMETER, email)
        }
        if (phone != null) {
            params.put(PHONE_PARAMETER, phone)
        }
        if (loyaltyId != null) {
            params.put(LOYALTY_ID_PARAMETER, loyaltyId)
        }
        if (externalId != null) {
            params.put(EXTERNAL_ID_PARAMETER, externalId)
        }
        if (category != null) {
            params.put(CATEGORY_PARAMETER, category)
        }
        if (label != null) {
            params.put(LABEL_PARAMETER, label)
        }
        if (value != null) {
            params.put(VALUE_PARAMETER, value)
        }

        postCustomEvent(params.build(), listener)
    }

    /**
     * An argument the call cannot take: `invalidArgument` behind the old code ([ArgumentRefusals]), off the
     * caller's thread on the new core.
     */
    private fun refuse(listener: OnApiCallbackListener?, code: Int, message: String?) =
        ArgumentRefusals.refuse(listener, code, message, offThread = sendNetworkMethodUseCase.queuesEvents)

    override fun trackPopupShown(popupId: Int, listener: OnApiCallbackListener?) {
        val params = Params()
        params.put(UserBasicParams.SHOP_ID, getUserSettingsValueUseCase.getShopId())
        params.put(UserBasicParams.DID, getUserSettingsValueUseCase.getDid())
        params.put(UserBasicParams.SID, getUserSettingsValueUseCase.getSid())
        params.put("popup", popupId.toString())

        sendNetworkMethodUseCase.postAsync(
            POPUP_SHOWN_PATH,
            params.build(),
            listener
        )
    }

    /**
     * Adds the source a host set with `tracking.setSource(...)`, if it is still inside its window.
     *
     * Port of iOS `TrackEventServiceImpl`: the source rides in its own `source` object and stays on
     * every request until it expires — it is not spent by the first one. Attached to `push` and
     * `push/custom` on the legacy core; `popup/showed` carries no attribution, on either platform. On
     * the new core the queue stamps it when the event is sent, on `push` only (contract `tracking`).
     */
    private fun attachStoredSource(body: JSONObject) {
        // The new core's queue puts it on /push when it sends the event, and nowhere else.
        if (sendNetworkMethodUseCase.queuesEvents) return
        val source = getTrackingSourceUseCase() ?: return
        try {
            body.put(
                SOURCE_PARAMETER,
                JSONObject()
                    .put(SOURCE_FROM_PARAMETER, source.type)
                    .put(SOURCE_CODE_PARAMETER, source.code)
            )
        } catch (e: JSONException) {
            SdkLogs.log(LogLevel.ERROR, LogCodes.REQUEST_BUILD_FAILED, "Request parameters could not be built", throwable = e)
        }
    }

    private fun postCustomEvent(json: JSONObject, listener: OnApiCallbackListener?) {
        attachStoredSource(json)
        val internalListener = answerListener(listener)

        sendNetworkMethodUseCase.postAsync(
            CUSTOM_PUSH_REQUEST,
            json,
            internalListener
        )
    }

    /** Popups v3 of this shop, if the host has them: custom events trigger them, a purchase refreshes the list. */
    private fun popups() = try {
        SdkRegistry.byShopId(getUserSettingsValueUseCase.getShopId())?.popupsV3
    } catch (e: Exception) {
        null
    }

    /**
     * Wraps the app's [listener]: a popup in the answer is shown, then the app hears the answer. On the
     * new core the queue may hold the popup back (decision 2: the event went late, or the app is in the
     * background); the app still gets the whole answer. A call of `tracking.queued` hears the queue's
     * acceptance through it ([EventAnswerListener.queued]).
     */
    private fun answerListener(listener: OnApiCallbackListener?) = object : EventAnswerListener() {
        override fun onSuccess(response: JSONObject?) {
            response?.let { handlePopup(it, popupHeldBack) }
            listener?.onSuccess(response)
        }

        override fun onError(code: Int, msg: String?) {
            listener?.onError(code, msg)
        }

        override fun queued() = (listener as? EventAnswerListener)?.queued()
    }

    private fun handlePopup(response: JSONObject, heldBack: String? = null) {
        val popUpData = response.optJSONObject(PARAM_POPUP)?.let { PopupDtoMapper.map(it) } ?: return
        if (heldBack != null) {
            // Held back on purpose (decision 2): a late event, an app in the background.
            SdkLogs.log(
                LogLevel.DEBUG,
                LogCodes.POPUP_NOT_SHOWN,
                "The popup of an event's answer is not shown",
                getUserSettingsValueUseCase.getShopId().takeIf { it.isNotEmpty() },
                mapOf("reason" to heldBack),
            )
            return
        }
        inAppNotificationManager.shopPopUp(popupDto = popUpData)
    }

    companion object {
        private const val CUSTOM_PUSH_REQUEST = "push/custom"
        private const val PUSH_REQUEST = "push"
        private const val POPUP_SHOWN_PATH = "popup/showed"

        private const val EVENT_PARAMETER = "event"
        private const val TIME_PARAMETER = "time"
        private const val PAYLOAD_PARAMETER = "payload"
        private const val EMAIL_PARAMETER = "email"
        private const val PHONE_PARAMETER = "phone"
        private const val LOYALTY_ID_PARAMETER = "loyalty_id"
        private const val EXTERNAL_ID_PARAMETER = "external_id"
        private const val CATEGORY_PARAMETER = "category"
        private const val LABEL_PARAMETER = "label"
        private const val VALUE_PARAMETER = "value"

        private const val SOURCE_PARAMETER = "source"
        private const val SOURCE_FROM_PARAMETER = "from"
        private const val SOURCE_CODE_PARAMETER = "code"
    }
}
