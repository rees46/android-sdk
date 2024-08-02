package com.personalization.utils

import android.content.SharedPreferences

internal object PreferencesUtils {

    internal fun saveField(sharedPreferences: SharedPreferences, fieldName: String, value: String) {
        val edit = sharedPreferences.edit()
        edit.putString(fieldName, value)
        edit.apply()
    }

    internal fun saveField(sharedPreferences: SharedPreferences, fieldName: String, value: Long) {
        val edit = sharedPreferences.edit()
        edit.putLong(fieldName, value)
        edit.apply()
    }
}