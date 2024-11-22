package com.personalization.errors

import android.util.Log
import org.json.JSONObject

/**
 * Class for handling JSON response errors and validation.
 */
class JsonResponseErrorHandler(
    private val tag: String,
    private val response: JSONObject?,
) {

    /**
     * Validates if the response is not null.
     * @return true if the response is valid, false otherwise.
     */
    fun validateResponse(): Boolean {
        return if (response == null) {
            logError("Response is null or incorrect.")
            false
        } else {
            Log.i(tag, "Response is : $response")
            true
        }
    }

    /**
     * Extracts a required string field from the JSON response.
     * Logs an error and returns null if the field is missing or empty.
     * @param fieldName The name of the field to extract.
     * @return The field value, or null if invalid.
     */
    fun getRequiredField(fieldName: String): String? {
        val value = response?.optString(fieldName)
        if (value.isNullOrEmpty()) {
            logError("Response does not contain the correct field: $fieldName.")
            return null
        }
        return value
    }

    /**
     * Logs the error message with the associated tag.
     * @param message The error message to log.
     */
    fun logError(message: String) {
        Log.e(tag, message)
    }
}
