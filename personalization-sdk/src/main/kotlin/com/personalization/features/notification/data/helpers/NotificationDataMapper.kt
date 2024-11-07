package com.personalization.features.notification.data.helpers

import com.google.firebase.messaging.RemoteMessage

object NotificationDataMapper {

    fun mapRemoteMessageToData(remoteMessage: RemoteMessage): MutableMap<String, String> {
        val data: MutableMap<String, String> = mutableMapOf()
        remoteMessage.notification?.let { notification ->
            notification.title?.let { if (it.isNotEmpty()) data["title"] = it }
            notification.body?.let { if (it.isNotEmpty()) data["body"] = it }
            notification.imageUrl?.let { data["image"] = it.toString() }
        }
        data.putAll(remoteMessage.data)
        return data
    }
}
