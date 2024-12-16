package com.personalization.features.notification.event.model

data class NotificationEvent(
    val type: String?,
    val uri: String?,
    val payload: Map<String, Any>? = null
)
