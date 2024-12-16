package com.personalization.sdk.data.models.dto.notification

import com.personalization.features.notification.actions.model.NotificationAction
import com.personalization.features.notification.event.model.NotificationEvent

data class NotificationData(
    val id: String?,
    val title: String?,
    val body: String?,
    val icon: String?,
    val type: String?,
    val actions: List<NotificationAction>?,
    val actionUrls: List<String>?,
    val image: String?,
    val event: NotificationEvent?
)
