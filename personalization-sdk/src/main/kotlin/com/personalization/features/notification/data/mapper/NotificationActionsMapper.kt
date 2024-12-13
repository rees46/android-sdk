package com.personalization.features.notification.data.mapper

import com.personalization.features.notification.domain.model.NotificationAction
import com.personalization.features.notification.domain.model.NotificationConstants.NOTIFICATION_ACTION
import com.personalization.features.notification.domain.model.NotificationConstants.NOTIFICATION_TITLE
import org.json.JSONArray

fun parseNotificationActions(actionsJson: String?): List<NotificationAction>? {
    return actionsJson?.let {
        try {
            val jsonArray = JSONArray(it)
            List(jsonArray.length()) { index ->
                val jsonObject = jsonArray.getJSONObject(index)
                NotificationAction(
                    action = jsonObject.getString(NOTIFICATION_ACTION),
                    title = jsonObject.getString(NOTIFICATION_TITLE)
                )
            }
        } catch (e: Exception) {
            null
        }
    }
}
