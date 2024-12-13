package com.personalization.features.notification.domain.model

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
