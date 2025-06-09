package com.personalization.features.trackEvent.impl

import com.personalization.Params
import com.personalization.Params.TrackEvent
import com.personalization.api.OnApiCallbackListener
import com.personalization.api.managers.InAppNotificationManager
import com.personalization.api.managers.TrackEventManager
import com.personalization.api.params.ProductItemParams
import com.personalization.sdk.data.mappers.popup.PopupDtoMapper
import com.personalization.sdk.data.models.params.SdkInitializationParams.PARAM_POPUP
import com.personalization.sdk.domain.usecases.network.SendNetworkMethodUseCase
import com.personalization.sdk.domain.usecases.recommendation.GetRecommendedByUseCase
import com.personalization.sdk.domain.usecases.recommendation.SetRecommendedByUseCase
import org.json.JSONObject
import javax.inject.Inject

internal class TrackEventManagerImpl @Inject constructor(
    val getRecommendedByUseCase: GetRecommendedByUseCase,
    val setRecommendedByUseCase: SetRecommendedByUseCase,
    private val sendNetworkMethodUseCase: SendNetworkMethodUseCase,
    private val inAppNotificationManager: InAppNotificationManager
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
        val lastRecommendedBy = getRecommendedByUseCase()
        if (lastRecommendedBy != null) {
            params.put(lastRecommendedBy)
            setRecommendedByUseCase(null)
        }

        val internalListener = object : OnApiCallbackListener() {
            override fun onSuccess(response: JSONObject?) {
                response?.let {
                    handlePopup(response)
                }
                listener?.onSuccess(response)
            }
        }

        sendNetworkMethodUseCase.postAsync(
            PUSH_REQUEST,
            params.build(),
            internalListener
        )
    }


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

        val internalListener = object : OnApiCallbackListener() {
            override fun onSuccess(response: JSONObject?) {
                response?.let {
                    handlePopup(response)
                }
                listener?.onSuccess(response)
            }
        }

        sendNetworkMethodUseCase.postAsync(
            CUSTOM_PUSH_REQUEST,
            params.build(),
            internalListener
        )
    }

    private fun handlePopup(response: JSONObject) {
        val popUpData = response.optJSONObject(PARAM_POPUP)?.let { PopupDtoMapper.map(it) }
        if (popUpData != null) {
            inAppNotificationManager.shopPopUp(popupDto = popUpData)
        }
    }

    companion object {
        private const val CUSTOM_PUSH_REQUEST = "push/custom"
        private const val PUSH_REQUEST = "push"

        private const val EVENT_PARAMETER = "event"
        private const val EMAIL_PARAMETER = "email"
        private const val PHONE_PARAMETER = "phone"
        private const val LOYALTY_ID_PARAMETER = "loyalty_id"
        private const val EXTERNAL_ID_PARAMETER = "external_id"
        private const val CATEGORY_PARAMETER = "category"
        private const val LABEL_PARAMETER = "label"
        private const val VALUE_PARAMETER = "value"
    }
}
