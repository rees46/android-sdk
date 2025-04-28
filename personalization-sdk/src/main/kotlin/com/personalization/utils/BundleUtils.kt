package com.personalization.utils

import android.os.Bundle

object BundleUtils {
    fun Bundle.getOptionalInt(key: String): Int? {
        return if (containsKey(key)) getInt(key) else null
    }
}
