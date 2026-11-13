package com.personalization.errors

import com.personalization.LogLevel
import com.personalization.logs.LogCodes
import com.personalization.logs.SdkLogs

class ActionsError(
    private val tag: String,
    private val functionName: String,
    private val actionName: String,
    private val message: String,
) {

    fun logError() {
        SdkLogs.log(LogLevel.ERROR, LogCodes.UI, "Error caught in $functionName : $message $actionName")
    }
}
