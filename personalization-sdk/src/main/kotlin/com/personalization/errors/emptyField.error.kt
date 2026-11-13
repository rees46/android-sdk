package com.personalization.errors

import com.personalization.LogLevel
import com.personalization.logs.LogCodes
import com.personalization.logs.SdkLogs

class EmptyFieldError(
    private val tag: String,
    private val functionName: String,
    private val message: String? = null,
) {

    fun logError() {
        SdkLogs.log(LogLevel.WARNING, LogCodes.INVALID_ARGUMENT, "Field is empty or null in $functionName : $message")
    }
}
