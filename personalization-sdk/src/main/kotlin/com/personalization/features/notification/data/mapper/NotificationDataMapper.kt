package com.personalization.features.notification.data.mapper

import com.google.firebase.messaging.RemoteMessage
import com.personalization.features.notification.domain.model.NotificationConstants
import com.personalization.features.notification.domain.model.NotificationData

fun RemoteMessage.toNotificationData(): NotificationData {
    var title: String? = null
    var body: String? = null
    var imageUrl: String? = null

    this.notification?.let { notification ->
        title = notification.title?.takeIf { it.isNotEmpty() }
        body = notification.body?.takeIf { it.isNotEmpty() }
        imageUrl = notification.imageUrl?.toString()
    }

    val analyticsLabel: String? =
        this.data[NotificationConstants.ANALYTICS_LABEL_FIELD]?.takeIf { it.isNotEmpty() }

    return NotificationData(
        title = title,
        body = body,
        images = imageUrl,
        analyticsLabel = analyticsLabel
    )
}
