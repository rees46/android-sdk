package com.personalization.api.params

import java.util.EnumSet

typealias NotificationChannels = EnumSet<NotificationChannel>

enum class NotificationChannel(val value: String) {
    EMAIL("email"),
    SMS("sms"),
    WEB_PUSH("web_push"),
    MOBILE_PUSH("mobile_push"),
    TELEGRAM("telegram"),
    WHATS_APP("WhatsApp");

    override fun toString(): String {
        return value
    }

    infix fun and(other: NotificationChannel): NotificationChannels =
        NotificationChannels.of(this, other)
}
