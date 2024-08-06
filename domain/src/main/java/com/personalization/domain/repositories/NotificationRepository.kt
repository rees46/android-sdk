package com.personalization.domain.repositories

import com.personalization.domain.models.NotificationSource

interface NotificationRepository {

    fun getNotificationSource(timeDuration: Int): NotificationSource?

    fun updateNotificationSource(type: String, id: String)
}
