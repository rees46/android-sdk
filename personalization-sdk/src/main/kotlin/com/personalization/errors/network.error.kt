package com.personalization.errors

import android.util.Log

class NetworkError(
    private val tag: String,
    private val functionName: String
) {

    fun logError() {
        Log.e(
            /* tag = */ tag,
            /* msg = */ "Error caught in $functionName : Network connection issue "
        )
    }
}
