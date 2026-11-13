package com.personalization.errors

import com.personalization.LogLevel
import com.personalization.logs.LogCodes
import com.personalization.logs.SdkLogs

class InvalidDomainError(
    private val tag: String,
    private val functionName: String
) {

    /**
     * Logs the error with a specific message
     */
    fun logError(message: String) {
        SdkLogs.log(LogLevel.ERROR, LogCodes.INVALID_CONFIG, "Error caught in $functionName: $message")
    }

    /**
     * Logs the error when an invalid domain is encountered
     */
    fun logInvalidDomainError(apiDomain: String) {
        logError("Invalid domain: $apiDomain")
    }

    /**
     * Logs the error when a blank domain is provided
     */
    fun logBlankDomainError() {
        logError("API domain cannot be blank")
    }
}
