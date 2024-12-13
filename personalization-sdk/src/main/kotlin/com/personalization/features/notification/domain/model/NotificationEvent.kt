package com.personalization.features.notification.domain.model

data class NotificationEvent(
    val type: String?,
    val uri: String?,
    val payload: Map<String, Any>? = null
)
