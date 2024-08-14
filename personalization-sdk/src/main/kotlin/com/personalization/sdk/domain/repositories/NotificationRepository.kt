package com.personalization.sdk.domain.repositories

import com.personalization.Params
import com.personalization.sdk.domain.models.NotificationSource

interface NotificationRepository {

    fun getNotificationSource(timeDuration: Int): NotificationSource?

    fun updateNotificationSource(type: String, id: String)

    fun getAllNotificationsParams(
        email: String?,
        phone: String?,
        loyaltyId: String?,
        externalId: String?,
        dateFrom: String,
        type: String,
        channel: String,
        page: Int?,
        limit: Int?
    ): Params
}
