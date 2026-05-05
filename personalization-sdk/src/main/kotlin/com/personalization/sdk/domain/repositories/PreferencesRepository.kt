package com.personalization.sdk.domain.repositories

import android.content.Context

interface PreferencesRepository {

    fun initialize(
        context: Context,
        preferencesKey: String
    )

    fun getToken(): String
    fun saveToken(value: String)

    fun getLastPushTokenDate(): Long
    fun saveLastPushTokenDate(value: Long)

    fun getHmsToken(): String
    fun saveHmsToken(value: String)

    fun getLastHmsPushTokenDate(): Long
    fun saveLastHmsPushTokenDate(value: Long)
}
