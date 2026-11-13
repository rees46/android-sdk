package com.personalization.errors

import com.personalization.LogLevel
import com.personalization.logs.LogCodes
import com.personalization.logs.SdkLogs

class NetworkError(
    private val tag: String,
    private val functionName: String
) {

    fun logError() {
        SdkLogs.log(LogLevel.WARNING, LogCodes.REQUEST_FAILED, "Error caught in $functionName : Network connection issue ")
    }
}
