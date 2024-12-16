package com.personalization.features.notification.actions.mapper

import com.personalization.errors.JsonResponseErrorHandler
import com.personalization.features.notification.actions.model.NotificationAction
import com.personalization.features.notification.domain.model.NotificationConstants.NOTIFICATION_ACTION
import com.personalization.features.notification.domain.model.NotificationConstants.NOTIFICATION_TITLE
import org.json.JSONArray

fun parseNotificationActions(actionsJson: String?): List<NotificationAction> {
    return try {
        actionsJson?.let {
            val jsonArray = JSONArray(it)
            List(jsonArray.length()) { index ->
                val jsonObject = jsonArray.getJSONObject(index)
                NotificationAction(
                    action = jsonObject.getString(NOTIFICATION_ACTION),
                    title = jsonObject.getString(NOTIFICATION_TITLE)
                )
            }
        } ?: emptyList()
    } catch (exception: Exception) {
        JsonResponseErrorHandler(
            tag = "parseNotificationActions",
            response = null
        ).logError(exception = exception)

        emptyList()
    }
}
