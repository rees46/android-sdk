package com.personalization.sdk.data.repositories.preferences

import android.content.Context
import android.content.SharedPreferences
import com.personalization.PreferencesPartition
import javax.inject.Inject
import javax.inject.Singleton

private const val DEFAULT_TOKEN = ""
private const val DEFAULT_LAST_PUSH_TOKEN_DATE = 0L

private const val PUSH_TOKEN_KEY_PREFIX = "push_token_"
private const val PUSH_TOKEN_DATE_KEY_PREFIX = "push_token_date_"

// Legacy keys kept for read-only fallback so existing installs are not re-registered.
private val LEGACY_TOKEN_KEYS = mapOf("fcm" to "token", "hms" to "hms_token")
private val LEGACY_TOKEN_DATE_KEYS =
    mapOf("fcm" to "last_push_token_date", "hms" to "last_hms_push_token_date")

@Singleton
class PreferencesDataSourceImpl @Inject constructor() : PreferencesDataSource {

    private var sharedPreferences: SharedPreferences? = null
    private var preferencesKey: String? = null

    override fun initialize(
        context: Context,
        preferencesKey: String,
        legacyPreferencesKey: String?,
        shopId: String?
    ) {
        val preferences = context.getSharedPreferences(preferencesKey, Context.MODE_PRIVATE)
        if (legacyPreferencesKey != null && shopId != null) {
            migrateLegacyIfNeeded(
                context = context,
                legacyPreferencesKey = legacyPreferencesKey,
                target = preferences,
                shopId = shopId
            )
        }
        this.sharedPreferences = preferences
        this.preferencesKey = preferencesKey
    }

    /**
     * One-time copy of the pre-partitioning shared preferences into this shop's partition, so an
     * existing single-instance install keeps its `did`/`sid`/segment/tokens after upgrading to
     * per-shop storage.
     *
     * Guards keep it safe and idempotent:
     *  - runs only while the partition is still empty, so it never overwrites data the SDK already
     *    wrote (and re-running after a completed migration is a no-op);
     *  - copies the legacy file only when it belongs to this shop — its stored `shop_id` matches, or
     *    is absent (a very old install). A legacy file left by a different shop is not pulled in, so
     *    that shop's partition stays clean under multi-instance.
     */
    private fun migrateLegacyIfNeeded(
        context: Context,
        legacyPreferencesKey: String,
        target: SharedPreferences,
        shopId: String
    ) {
        if (target.all.isNotEmpty()) return

        val legacy = context.getSharedPreferences(legacyPreferencesKey, Context.MODE_PRIVATE)
        val legacyEntries = legacy.all
        if (legacyEntries.isEmpty()) return

        val legacyShopId = legacy.getString(PreferencesPartition.SHOP_ID_FIELD, "").orEmpty()
        if (legacyShopId.isNotEmpty() && legacyShopId != shopId) return

        with(target.edit()) {
            for ((key, value) in legacyEntries) {
                when (value) {
                    is String -> putString(key, value)
                    is Long -> putLong(key, value)
                    is Int -> putInt(key, value)
                    is Float -> putFloat(key, value)
                    is Boolean -> putBoolean(key, value)
                    is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
                }
            }
            apply()
        }
    }

    override fun getPushToken(provider: String): String {
        val value = getValue(PUSH_TOKEN_KEY_PREFIX + provider, DEFAULT_TOKEN)
        if (value.isNotEmpty()) return value
        val legacyKey = LEGACY_TOKEN_KEYS[provider] ?: return value
        return getValue(legacyKey, DEFAULT_TOKEN)
    }

    override fun savePushToken(provider: String, value: String) =
        saveValue(PUSH_TOKEN_KEY_PREFIX + provider, value)

    override fun getLastPushTokenDate(provider: String): Long {
        val value = getValue(PUSH_TOKEN_DATE_KEY_PREFIX + provider, DEFAULT_LAST_PUSH_TOKEN_DATE)
        if (value != DEFAULT_LAST_PUSH_TOKEN_DATE) return value
        val legacyKey = LEGACY_TOKEN_DATE_KEYS[provider] ?: return value
        return getValue(legacyKey, DEFAULT_LAST_PUSH_TOKEN_DATE)
    }

    override fun saveLastPushTokenDate(provider: String, value: Long) =
        saveValue(PUSH_TOKEN_DATE_KEY_PREFIX + provider, value)

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
