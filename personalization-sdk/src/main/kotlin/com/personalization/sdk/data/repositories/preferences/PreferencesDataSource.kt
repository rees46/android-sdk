package com.personalization.sdk.data.repositories.preferences

import android.content.Context

interface PreferencesDataSource {

    /**
     * Opens the preferences partition named [preferencesKey] of [shopId]'s instance. The data of older
     * releases is read into it before, by [com.personalization.core.internal.StorageMigrations].
     */
    fun initialize(
        context: Context,
        preferencesKey: String,
        shopId: String? = null
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
