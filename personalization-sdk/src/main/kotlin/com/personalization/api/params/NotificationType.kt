package com.personalization.api.params

import java.util.EnumSet

typealias NotificationTypes = EnumSet<NotificationType>

enum class NotificationType(val value: String) {
    BULK("bulk"),
    TRIGGER("trigger"),
    TRANSACTIONAL("transactional"),
    CHAIN("chain");

    override fun toString(): String {
        return value
    }

    infix fun and(other: NotificationType): NotificationTypes =
        NotificationTypes.of(this, other)
}
