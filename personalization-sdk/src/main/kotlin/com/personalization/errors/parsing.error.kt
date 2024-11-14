package com.personalization.errors

import android.util.Log

class ParsingJsonError(
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
