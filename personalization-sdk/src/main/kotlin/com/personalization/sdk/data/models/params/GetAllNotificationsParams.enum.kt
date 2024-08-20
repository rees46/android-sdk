package com.personalization.sdk.data.models.params

class GetAllNotificationsParams {
    companion object {
        const val EMAIL = "email"
        const val PHONE = "phone"
        const val EXTERNAL_ID = "external_id"
        const val LOYALTY_ID = "loyalty_id"
        const val DATE_FROM = "date_from"
        const val TYPE = "type"
        const val CHANNEL = "channel"
        const val PAGE = "page"
        const val LIMIT = "limit"
    }
}
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
