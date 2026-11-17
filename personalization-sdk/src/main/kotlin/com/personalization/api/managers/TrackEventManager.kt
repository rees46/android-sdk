package com.personalization.api.managers

import com.personalization.Params
import com.personalization.Params.TrackEvent
import com.personalization.api.OnApiCallbackListener
import com.personalization.api.models.purchase.PurchaseTrackingRequest

/**
 * The tracking methods of earlier releases; [TrackingApi] (`sdk.tracking`) replaces them.
 *
 * On the new core (`Rees46.initializeBridge`) every event goes through the queue: the method returns at
 * once, and a `listener` is kept until the event is sent — after a launch without a network that may be
 * as long as the queue keeps an event, 7 days — then hears the server's answer, or the error that
 * dropped the event. Whatever the listener holds stays alive that long.
 */
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
     * Strict purchase tracking (`event` = `purchase`) with typed request (parity with iOS / React Native).
     */
    fun trackPurchase(
        request: PurchaseTrackingRequest,
        listener: OnApiCallbackListener? = null,
    )

    /**
     * Custom event tracking (aligned with the iOS SDK `trackEvent`).
     *
     * @param event Event key
     * @param time Optional UNIX time (seconds), same as iOS
     * @param category Optional category
     * @param label Optional label
     * @param value Optional value (sent as string in JSON, same as iOS)
     * @param customFields Optional map merged at the top level and duplicated under `payload` (payload only contains these entries)
     * @param listener Callback
     */
    fun trackEvent(
        event: String,
        time: Int? = null,
        category: String? = null,
        label: String? = null,
        value: Int? = null,
        customFields: Map<String, Any?>? = null,
        listener: OnApiCallbackListener? = null
    )

    /**
     * Tracking custom events (legacy).
     *
     * Use [trackEvent] for the standard event shape (`time`, `customFields`).
     * This method remains for optional identity fields: `email`, `phone`, `loyalty_id`, `external_id`.
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
    @Deprecated(
        message = "Use trackEvent(event, time, category, label, value, customFields, listener) for the iOS-aligned API. " +
            "Use this method only when you still need email, phone, loyalty_id, or external_id.",
        replaceWith = ReplaceWith(
            expression = "trackEvent(event = event, time = null, category = category, label = label, value = value, customFields = null, listener = listener)",
            imports = []
        )
    )
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
