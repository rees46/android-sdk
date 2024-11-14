package com.personalization.errors

import android.util.Log

class ResourceLoadError(
    private val tag: String,
    private val functionName: String,
    private val message: String
) {

    fun logError() {
        Log.e(
            /* tag = */ tag,
            /* msg = */ "Error caught in $functionName: $message"
        )
    }
}
