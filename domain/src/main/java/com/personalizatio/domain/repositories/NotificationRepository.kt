package com.personalizatio.domain.repositories

import com.personalizatio.domain.models.NotificationSource

interface NotificationRepository {

    fun getNotificationSource(timeDuration: Int): NotificationSource?

    fun updateNotificationSource(type: String, id: String)
}
