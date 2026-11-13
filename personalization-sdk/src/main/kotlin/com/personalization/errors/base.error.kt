package com.personalization.errors

import com.personalization.LogLevel
import com.personalization.logs.LogCodes
import com.personalization.logs.SdkLogs

class BaseInfoError(
    private val tag: String,
    private val exception: Exception? = null,
    private val message: String
) {

    fun logError() {
        SdkLogs.log(LogLevel.ERROR, LogCodes.SDK_LOG, "$message: ${exception?.javaClass?.simpleName ?: "unknown error"}")
    }
}
