package com.personalization.sdk.domain.repositories

import android.content.Context

interface PreferencesRepository {

    /**
     * Opens the shop's partition [preferencesKey]. The parameters are those of earlier releases, so a call
     * written for them binds as it did: [legacyPreferencesKey] is not read any more, the SDK reads the
     * store of older releases into the shop's partition itself, once, before this is called.
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
}
