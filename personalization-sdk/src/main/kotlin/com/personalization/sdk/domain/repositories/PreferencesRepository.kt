package com.personalization.sdk.domain.repositories

import android.content.Context

interface PreferencesRepository {

    fun initialize(
        context: Context,
        preferencesKey: String,
        shopId: String? = null
    )

    /**
     * The form of earlier releases. [legacyPreferencesKey] is not read any more: the SDK reads the store
     * of older releases into the shop's partition itself, once, before this is called.
     */
    @Deprecated(
        message = "legacyPreferencesKey is ignored: the SDK migrates the store of older releases itself.",
        replaceWith = ReplaceWith("initialize(context, preferencesKey, shopId)")
    )
    fun initialize(
        context: Context,
        preferencesKey: String,
        legacyPreferencesKey: String?,
        shopId: String? = null
    ) = initialize(context = context, preferencesKey = preferencesKey, shopId = shopId)

    fun getPushToken(provider: String): String
    fun savePushToken(provider: String, value: String)

    fun getLastPushTokenDate(provider: String): Long
    fun saveLastPushTokenDate(provider: String, value: Long)
}
