package com.personalization.sdk.data.repositories.preferences

import android.content.Context
import android.content.SharedPreferences
import kotlin.math.roundToInt

class PreferencesDataSource {

    private var sharedPreferences: SharedPreferences? = null
    private var preferencesKey: String? = null

    internal fun initialize(
        context: Context,
        preferencesKey: String
    ) {
        this.sharedPreferences = context.getSharedPreferences(preferencesKey, Context.MODE_PRIVATE)
        this.preferencesKey = preferencesKey
    }

    internal fun getToken(): String = getValue(TOKEN_KEY, DEFAULT_TOKEN)
    internal fun saveToken(value: String) = saveValue(TOKEN_KEY, value)

    internal fun getLastPushTokenDate() = getValue(LAST_PUSH_TOKEN_DATE_KEY, DEFAULT_LAST_PUSH_TOKEN_DATE)
    internal fun saveLastPushTokenDate(value: Long) = saveValue(LAST_PUSH_TOKEN_DATE_KEY, value)

    internal fun getSegment(): String {
        val field = preferencesKey + SEGMENT_KEY
        return getValue(field, DEFAULT_SEGMENT)
    }

    internal fun getValue(field: String, defaultValue: String): String = sharedPreferences?.getString(field, defaultValue) ?: defaultValue
    internal fun getValue(field: String, defaultValue: Long): Long = sharedPreferences?.getLong(field, defaultValue) ?: defaultValue

    internal fun<T> saveValue(field: String, value: T) {
        val putEditor = sharedPreferences?.let { sharedPreferences ->
            with(sharedPreferences.edit()) {
                when (value) {
                    is Boolean -> putBoolean(field, value)
                    is String -> putString(field, value)
                    is Long -> putLong(field, value)
                    is Float -> putFloat(field, value)
                    is Int -> putInt(field, value)
                    else -> null
                }
            }
        }

        putEditor?.apply()
    }

    companion object {
        private const val DEFAULT_TOKEN = ""
        private const val DEFAULT_LAST_PUSH_TOKEN_DATE = 0L
        private val DEFAULT_SEGMENT = arrayOf("A", "B")[Math.random().roundToInt()]

        private const val TOKEN_KEY = "token"
        private const val LAST_PUSH_TOKEN_DATE_KEY = "last_push_token_date"
        private const val SEGMENT_KEY = ".segment"
    }
}
