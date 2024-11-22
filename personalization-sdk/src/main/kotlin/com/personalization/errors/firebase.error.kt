package com.personalization.errors

import android.util.Log

class FirebaseError(
    private val tag: String,
    private val exception: Exception?
) {

    fun logError() {
        Log.e(
            /* tag = */ tag,
            /* msg = */
            "Failed to retrieve Firebase token: ${exception?.message ?: "Unknown error"}"
        )
    }
}
