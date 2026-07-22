package com.personalization.sdk.data.repositories.preferences

import android.content.Context

interface PreferencesDataSource {

    fun initialize(
        context: Context,
        preferencesKey: String
    )

    fun getPushToken(provider: String): String

    fun savePushToken(provider: String, value: String)

    fun getLastPushTokenDate(provider: String): Long

    fun saveLastPushTokenDate(provider: String, value: Long)

    fun getValue(field: String, defaultValue: String): String

    fun getValue(field: String, defaultValue: Long): Long

    fun <T> saveValue(field: String, value: T)

    fun removeValue(field: String)
}
