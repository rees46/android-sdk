package com.personalization

import com.personalization.sdk.data.models.dto.notification.NotificationData

fun interface OnMessageListener {
    fun onMessage(data: NotificationData)
}
