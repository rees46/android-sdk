package com.personalizatio.data.repositories.preferences

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

    internal fun getSidLastActTime(): Long = getValue(SID_LAST_ACT_KEY, DEFAULT_SID_LAST_ACT_TIME)
    internal fun saveSidLastActTime(value: Long) = saveValue(SID_LAST_ACT_KEY, value)

    internal fun getSid(): String = getValue(SID_KEY, DEFAULT_SID)
    internal fun saveSid(value: String) = saveValue(SID_KEY, value)

    internal fun getDid(): String = getValue(DID_KEY, DEFAULT_DID)
    internal fun saveDid(value: String) = saveValue(DID_KEY, value)

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
        private const val DEFAULT_SID = ""
        private const val DEFAULT_SID_LAST_ACT_TIME = 0L
        private const val DEFAULT_DID = ""
        private const val DEFAULT_TOKEN = ""
        private const val DEFAULT_LAST_PUSH_TOKEN_DATE = 0L
        private val DEFAULT_SEGMENT = arrayOf("A", "B")[Math.random().roundToInt()]

        private const val SID_KEY = "sid"
        private const val SID_LAST_ACT_KEY = "sid_last_act"
        private const val DID_KEY = "did"
        private const val TOKEN_KEY = "token"
        private const val LAST_PUSH_TOKEN_DATE_KEY = "last_push_token_date"
        private const val SEGMENT_KEY = ".segment"
    }
}
