package com.personalization.features.notification.actions.mapper

import com.personalization.errors.JsonResponseErrorHandler
import org.json.JSONArray

fun parseActionUrls(actionUrlsJson: String?): List<String> {
    return try {
        actionUrlsJson?.let {
            val jsonArray = JSONArray(it)
            List(jsonArray.length()) { index -> jsonArray.getString(index) }
        } ?: emptyList()
    } catch (exception: Exception) {
        JsonResponseErrorHandler(
            tag = "parseNotificationActions",
            response = null
        ).logError(exception = exception)
        emptyList()
    }
}
