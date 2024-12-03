package com.personalization.errors

import android.util.Log

class EmptyFieldError(
    private val tag: String,
    private val functionName: String,
    private val message: String? = null,
) {

    fun logError() {
        Log.e(
            /* tag = */ tag,
            /* msg = */ "Field is empty or null in $functionName : $message"
        )
    }
}
