package com.personalization.features.track_event

import com.personalization.Params
import com.personalization.Params.TrackEvent
import com.personalization.api.OnApiCallbackListener
import com.personalization.api.managers.NetworkManager
import com.personalization.api.managers.TrackEventManager
import com.personalization.api.params.ProductItemParams
import com.personalization.sdk.domain.usecases.recommendation.GetRecommendedByUseCase
import com.personalization.sdk.domain.usecases.recommendation.SetRecommendedByUseCase
import javax.inject.Inject

internal class TrackEventManagerImpl @Inject constructor(
    val networkManager: NetworkManager,
    val getRecommendedByUseCase: GetRecommendedByUseCase,
    val setRecommendedByUseCase: SetRecommendedByUseCase
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
        networkManager.postAsync(PUSH_REQUEST, params.build(), listener)
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
        if (email != null) { params.put(EMAIL_PARAMETER, email) }
        if (phone != null) { params.put(PHONE_PARAMETER, phone) }
        if (loyaltyId != null) { params.put(LOYALTY_ID_PARAMETER, loyaltyId) }
        if (externalId != null) { params.put(EXTERNAL_ID_PARAMETER, externalId) }
        if (category != null) { params.put(CATEGORY_PARAMETER, category) }
        if (label != null) { params.put(LABEL_PARAMETER, label) }
        if (value != null) { params.put(VALUE_PARAMETER, value) }

        networkManager.postAsync(CUSTOM_PUSH_REQUEST, params.build(), listener)
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
