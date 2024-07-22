package com.personalizatio.data.repositories.preferences

import android.content.SharedPreferences
import android.content.SharedPreferences.Editor

class PreferencesDataSource {

    private lateinit var sharedPreferences: SharedPreferences

    fun init(sharedPreferences: SharedPreferences) {
        this.sharedPreferences = sharedPreferences
    }

    fun<T> getValue(field: String, defaultValue: T?): Any? {
        with(sharedPreferences) {
            when (defaultValue) {
                is Boolean -> return getBoolean(field, defaultValue)
                is String -> return getString(field, defaultValue)
                is Long -> return getLong(field, defaultValue)
                is Float -> return getFloat(field, defaultValue)
                is Int -> return getInt(field, defaultValue)
                else -> return null
            }
        }
    }

    fun<T> saveValue(field: String, value: T) {
        putEditor(field, value)?.apply()
    }

    private fun<T> putEditor(field: String, value: T) : Editor? {
        with(sharedPreferences.edit()) {
            when (value) {
                is Boolean -> return putBoolean(field, value)
                is String -> return putString(field, value)
                is Long -> return putLong(field, value)
                is Float -> return putFloat(field, value)
                is Int -> return putInt(field, value)
                else -> return null
            }
        }
    }
}
