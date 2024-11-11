package com.personalization.features.notification.data.mapper

import com.google.firebase.messaging.RemoteMessage
import com.personalization.features.notification.domain.model.NotificationConstants.ANALYTICS_LABEL_FIELD
import com.personalization.features.notification.domain.model.NotificationConstants.NOTIFICATION_BODY
import com.personalization.features.notification.domain.model.NotificationConstants.NOTIFICATION_IMAGES
import com.personalization.features.notification.domain.model.NotificationConstants.NOTIFICATION_TITLE

object NotificationDataMapper {

    fun mapRemoteMessageToData(remoteMessage: RemoteMessage): MutableMap<String, String> {
        val data: MutableMap<String, String> = HashMap(remoteMessage.data)
        remoteMessage.notification?.let { notification ->
            addNotificationData(
                notification = notification,
                data = data
            )
        }
        data[NOTIFICATION_IMAGES]?.takeIf { it.isNotEmpty() }
            ?.let { data[NOTIFICATION_IMAGES] = it }
        data[ANALYTICS_LABEL_FIELD]?.takeIf { it.isNotEmpty() }
            ?.let { data[ANALYTICS_LABEL_FIELD] = it }
        return data
    }

    private fun addNotificationData(
        notification: RemoteMessage.Notification,
        data: MutableMap<String, String>
    ) {
        notification.title?.takeIf { it.isNotEmpty() }?.let { data[NOTIFICATION_TITLE] = it }
        notification.body?.takeIf { it.isNotEmpty() }?.let { data[NOTIFICATION_BODY] = it }
        notification.imageUrl?.let { data[NOTIFICATION_IMAGES] = it.toString() }
    }
}
