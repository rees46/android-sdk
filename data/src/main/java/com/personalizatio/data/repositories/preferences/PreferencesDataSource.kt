package com.personalizatio.data.repositories.preferences

import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.Editor

class PreferencesDataSource {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var preferencesKey: String

    internal fun initialize(
        context: Context,
        preferencesKey: String
    ) {
        this.sharedPreferences = context.getSharedPreferences(preferencesKey, Context.MODE_PRIVATE)
        this.preferencesKey = preferencesKey
    }

    internal fun getSidLastActTime(defaultValue: Long) = getValue(SID_LAST_ACT_KEY, defaultValue)
    internal fun saveSidLastActTime(value: Long) = saveValue(SID_LAST_ACT_KEY, value)

    internal fun getSid(defaultValue: String?) = getValue(SID_KEY, defaultValue)
    internal fun saveSid(value: String) = saveValue(SID_KEY, value)

    internal fun getDid(defaultValue: String?) = getValue(DID_KEY, defaultValue)
    internal fun saveDid(value: String) = saveValue(DID_KEY, value)

    internal fun getToken(defaultValue: String?) = getValue(TOKEN_KEY, defaultValue)
    internal fun saveToken(value: String) = saveValue(TOKEN_KEY, value)

    internal fun getLastPushTokenDate(defaultValue: Long) = getValue(LAST_PUSH_TOKEN_DATE_KEY, defaultValue)
    internal fun saveLastPushTokenDate(value: Long) = saveValue(LAST_PUSH_TOKEN_DATE_KEY, value)

    internal fun getSegment(defaultValue: String): String {
        val field = preferencesKey + SEGMENT_KEY
        return getValue(field, defaultValue) ?: defaultValue
    }

    internal fun getValue(field: String, defaultValue: String?) = sharedPreferences.getString(field, defaultValue)
    internal fun getValue(field: String, defaultValue: Long) = sharedPreferences.getLong(field, defaultValue)

    internal fun<T> saveValue(field: String, value: T) =
        putEditor(field, value)?.apply()

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

    companion object {
        private const val SEGMENT_KEY = ".segment"
        private const val SID_KEY = "sid"
        private const val SID_LAST_ACT_KEY = "sid_last_act"
        private const val DID_KEY = "did"
        private const val TOKEN_KEY = "token"
        private const val LAST_PUSH_TOKEN_DATE_KEY = "last_push_token_date"
    }
}
