package com.personalization.sdk.data.repositories.notification

import com.personalization.sdk.data.models.NotificationSourceDto

interface NotificationDataSource {

    fun getNotificationSourceDto(): NotificationSourceDto

    fun saveType(value: String)

    fun saveId(value: String)

    fun saveTime(value: Long)
}
