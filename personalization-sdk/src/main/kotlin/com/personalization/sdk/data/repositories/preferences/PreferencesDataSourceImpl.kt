package com.personalization.sdk.data.repositories.preferences

import android.content.Context
import android.content.SharedPreferences
import javax.inject.Inject
import javax.inject.Singleton

private const val DEFAULT_TOKEN = ""
private const val DEFAULT_LAST_PUSH_TOKEN_DATE = 0L

private const val TOKEN_KEY = "token"
private const val LAST_PUSH_TOKEN_DATE_KEY = "last_push_token_date"

@Singleton
class PreferencesDataSourceImpl @Inject constructor() : PreferencesDataSource {

    private var sharedPreferences: SharedPreferences? = null
    private var preferencesKey: String? = null

    override fun initialize(
        context: Context,
        preferencesKey: String
    ) {
        this.sharedPreferences = context.getSharedPreferences(preferencesKey, Context.MODE_PRIVATE)
        this.preferencesKey = preferencesKey
    }

    override fun getToken(): String = getValue(TOKEN_KEY, DEFAULT_TOKEN)
    override fun saveToken(value: String) = saveValue(TOKEN_KEY, value)

    override fun getLastPushTokenDate(): Long {
        return getValue(
            field = LAST_PUSH_TOKEN_DATE_KEY,
            defaultValue = DEFAULT_LAST_PUSH_TOKEN_DATE
        )
    }

    override fun saveLastPushTokenDate(value: Long) {
        saveValue(
            field = LAST_PUSH_TOKEN_DATE_KEY,
            value = value
        )
    }

    override fun getValue(field: String, defaultValue: String): String {
        return sharedPreferences?.getString(field, defaultValue) ?: defaultValue
    }

    override fun getValue(field: String, defaultValue: Long): Long {
        return sharedPreferences?.getLong(field, defaultValue) ?: defaultValue
    }

    override fun <T> saveValue(field: String, value: T) {
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

    override fun removeValue(field: String) {
        sharedPreferences?.edit()?.remove(field)?.apply()
    }
}
