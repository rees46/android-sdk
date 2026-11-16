package com.personalization.errors

import com.personalization.LogLevel
import com.personalization.logs.LogCodes
import com.personalization.logs.SdkLogs

class ResourceLoadError(
    private val tag: String,
    private val functionName: String,
    private val message: String
) {

    fun logError() {
        SdkLogs.log(LogLevel.ERROR, LogCodes.UI, "Error caught in $functionName: $message")
    }
}
