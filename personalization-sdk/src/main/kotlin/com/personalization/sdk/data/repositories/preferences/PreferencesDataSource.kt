package com.personalization.sdk.data.repositories.preferences

import android.content.Context

interface PreferencesDataSource {

    fun initialize(
        context: Context,
        preferencesKey: String
    )

    fun getToken(): String

    fun saveToken(value: String)

    fun getLastPushTokenDate(): Long

    fun saveLastPushTokenDate(value: Long)

    fun getValue(field: String, defaultValue: String): String

    fun getValue(field: String, defaultValue: Long): Long

    fun <T> saveValue(field: String, value: T)

    fun removeValue(field: String)
}
