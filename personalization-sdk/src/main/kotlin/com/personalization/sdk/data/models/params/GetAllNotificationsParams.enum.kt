package com.personalization.sdk.data.models.params

enum class GetAllNotificationsParams(val value: String) {
    EMAIL("email"),
    PHONE("phone"),
    EXTERNAL_ID("external_id"),
    LOYALTY_ID("loyalty_id"),
    DATE_FROM("date_from"),
    TYPE("type"),
    CHANNEL("channel"),
    PAGE("page"),
    LIMIT("limit")
}
