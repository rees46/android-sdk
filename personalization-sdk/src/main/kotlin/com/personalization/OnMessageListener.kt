package com.personalization

import com.personalization.features.notification.domain.model.NotificationData

fun interface OnMessageListener {
    fun onMessage(data: NotificationData)
}
