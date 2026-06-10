package com.personalization.sdk.domain.repositories

import android.content.Context

interface PreferencesRepository {

    fun initialize(
        context: Context,
        preferencesKey: String
    )

    fun getPushToken(provider: String): String
    fun savePushToken(provider: String, value: String)

    fun getLastPushTokenDate(provider: String): Long
    fun saveLastPushTokenDate(provider: String, value: Long)
}
