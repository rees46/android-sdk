package com.personalization.features.notification.domain.model

data class NotificationData(
    val title: String?,
    val body: String?,
    val images: String?,
    val analyticsLabel: String? = null
)
