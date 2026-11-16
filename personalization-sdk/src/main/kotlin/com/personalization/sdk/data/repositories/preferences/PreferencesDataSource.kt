package com.personalization.sdk.data.repositories.preferences

import android.content.Context

interface PreferencesDataSource {

    /**
     * Opens the preferences partition named [preferencesKey] of [shopId]'s instance. The data of older
     * releases is read into it before, by [com.personalization.core.internal.StorageMigrations].
     *
     * The parameters are those of earlier releases, so a call written for them binds as it did:
     * [legacyPreferencesKey] is not read any more, the SDK migrates the store of older releases itself.
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
