package com.personalization.features.notification.data.mapper

import org.json.JSONArray

fun parseActionUrls(actionUrlsJson: String?): List<String>? {
    return actionUrlsJson?.let {
        try {
            val jsonArray = JSONArray(it)
            List(jsonArray.length()) { index -> jsonArray.getString(index) }
        } catch (e: Exception) {
            null
        }
    }
}
