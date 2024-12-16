package com.personalization.features.notification.data.mapper

import com.personalization.errors.JsonResponseErrorHandler
import com.personalization.features.notification.domain.model.NotificationConstants.NOTIFICATION_PAYLOAD
import com.personalization.features.notification.domain.model.NotificationConstants.NOTIFICATION_URI
import com.personalization.features.notification.domain.model.NotificationConstants.TYPE_PARAM
import com.personalization.features.notification.domain.model.NotificationEvent
import org.json.JSONObject

fun parseNotificationEvent(eventJson: String?): NotificationEvent {
    val emptyData = NotificationEvent(
        type = "",
        uri = "",
        payload = emptyMap()
    )
    return try {
        eventJson?.let {
            val jsonObject = JSONObject(it)

            NotificationEvent(
                type = jsonObject.optString(TYPE_PARAM, ""),
                uri = jsonObject.optString(NOTIFICATION_URI, ""),
                payload = jsonObject.optJSONObject(NOTIFICATION_PAYLOAD)?.let { payloadObj ->
                    payloadObj.keys().asSequence().associateWith { key -> payloadObj[key] }
                } ?: emptyMap()
            )

        } ?: emptyData

    } catch (exception: Exception) {
        JsonResponseErrorHandler(
            tag = "parseNotificationEvent",
            response = null
        ).logError(exception = exception)

        emptyData
    }
}
