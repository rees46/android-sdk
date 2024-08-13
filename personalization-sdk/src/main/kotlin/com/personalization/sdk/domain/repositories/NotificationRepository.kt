package com.personalization.sdk.domain.repositories

import com.personalization.sdk.domain.models.NotificationSource

interface NotificationRepository {

    fun getNotificationSource(timeDuration: Int): NotificationSource?

    fun updateNotificationSource(type: String, id: String)
}
