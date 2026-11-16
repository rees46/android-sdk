package com.personalization.errors

import com.personalization.LogLevel
import com.personalization.logs.LogCodes
import com.personalization.logs.SdkLogs

class FirebaseError(
    private val tag: String,
    private val exception: Exception?
) {

    fun logError() {
        SdkLogs.log(LogLevel.WARNING, LogCodes.PUSH_TOKEN_FAILED, "Failed to retrieve Firebase token: ${exception?.javaClass?.simpleName ?: "unknown error"}")
    }
}
