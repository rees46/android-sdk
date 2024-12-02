package com.personalization.errors

import android.util.Log

class BaseInfoError(
    private val tag: String,
    private val exception: Exception? = null,
    private val message: String
) {

    fun logError() {
        Log.e(
            /* tag = */ tag,
            /* msg = */"$message: ${exception?.message ?: "Unknown error"}"
        )
    }
}
