package com.personalization.sdk.data.repositories.preferences

import android.content.Context

interface PreferencesDataSource {

    /**
     * Opens the preferences partition named [preferencesKey]. When [legacyPreferencesKey] and
     * [shopId] are provided, a one-time migration copies the legacy shared file into the partition
     * if it is still empty and the legacy file belongs to [shopId].
     */
    fun initialize(
        context: Context,
        preferencesKey: String,
        legacyPreferencesKey: String? = null,
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
