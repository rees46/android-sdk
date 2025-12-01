package com.personalization.api.managers

import com.personalization.Params
import com.personalization.Params.TrackEvent
import com.personalization.api.OnApiCallbackListener

interface TrackEventManager {

    /**
     * Event tracking
     *
     * @param event Event type
     * @param productId Product ID
     */
    fun track(event: TrackEvent, productId: String)

    /**
     * Event tracking
     *
     * @param event Event type
     * @param params Parameters for the request
     * @param listener Callback
     */
    fun track(
        event: TrackEvent,
        params: Params,
        listener: OnApiCallbackListener? = null
    )

    /**
     * Tracking custom events
     *
     * @param event Event key
     * @param email Email
     * @param phone Phone
     * @param loyaltyId Loyalty ID
     * @param externalId External ID
     * @param category Event category
     * @param label Event label
     * @param value Event value
     * @param listener Callback
     */
    fun customTrack(
        event: String,
        email: String? = null,
        phone: String? = null,
        loyaltyId: String? = null,
        externalId: String? = null,
        category: String? = null,
        label: String? = null,
        value: Int? = null,
        listener: OnApiCallbackListener? = null
    )

    /**
     * Track popup shown event
     *
     * @param popupId Popup ID
     * @param listener Callback
     */
    fun trackPopupShown(popupId: Int, listener: OnApiCallbackListener? = null)
}
