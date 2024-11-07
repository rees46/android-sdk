package com.personalization.notification

import com.personalization.SDK

object ErrorHandler {

    /**
     * Log an error message along with an optional exception.
     * @param message Error message to log.
     * @param exception Optional exception for logging.
     */
    fun logError(message: String, exception: Exception? = null) {
        SDK.error(message, exception)
    }
}
