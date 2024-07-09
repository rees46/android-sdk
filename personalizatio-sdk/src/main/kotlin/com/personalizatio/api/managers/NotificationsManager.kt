package com.personalizatio.api.managers

import com.personalizatio.api.OnApiCallbackListener

interface NotificationsManager {

    /**
     * Request a all notifications.
     * At least of identifiers must present in request: email, phone, loyaltyId, externalId. It's used to identify user.
     *
     * @param email Email, if available
     * @param phone Phone, if available
     * @param loyaltyId Loyalty ID, if available
     * @param externalId External ID, if available
     * @param listener Callback
     */
    fun getAllNotifications(
        email: String? = null,
        phone: String? = null,
        loyaltyId: String? = null,
        externalId: String? = null,
        dateFrom: String,
        type: String,
        channel: String,
        page: Int? = null,
        limit: Int? = null,
        listener: OnApiCallbackListener? = null
    )
}
