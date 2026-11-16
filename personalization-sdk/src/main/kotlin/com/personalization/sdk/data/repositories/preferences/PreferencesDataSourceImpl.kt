package com.personalization.sdk.data.repositories.preferences

import android.content.Context
import android.content.SharedPreferences
import com.personalization.LogLevel
import com.personalization.core.internal.DeviceStorage
import com.personalization.core.internal.StorageKeys
import com.personalization.logs.LogCodes
import com.personalization.logs.SdkLogs
import javax.inject.Inject
import javax.inject.Singleton

private const val DEFAULT_TOKEN = ""
private const val DEFAULT_LAST_PUSH_TOKEN_DATE = 0L

@Singleton
class PreferencesDataSourceImpl @Inject constructor() : PreferencesDataSource {

    private var sharedPreferences: SharedPreferences? = null
    private var preferencesKey: String? = null
    private var device: DeviceStorage? = null
    private var shopId: String? = null

    override fun initialize(
        context: Context,
        preferencesKey: String,
        legacyPreferencesKey: String?,
        shopId: String?
    ) {
        // The data of older releases is read into the partition before this, by StorageMigrations.
        val preferences = context.getSharedPreferences(preferencesKey, Context.MODE_PRIVATE)
        this.sharedPreferences = preferences
        this.preferencesKey = preferencesKey
        this.device = DeviceStorage(context)
        this.shopId = shopId
    }

    override fun getPushToken(provider: String): String {
        val value = getValue(StorageKeys.pushToken(provider), DEFAULT_TOKEN)
        if (value.isNotEmpty()) return value
        val legacyKey = StorageKeys.LEGACY_PUSH_TOKEN[provider] ?: return value
        return getValue(legacyKey, DEFAULT_TOKEN)
    }

    /** The shop's copy, which the legacy path reads, and the device's, which belongs to every shop. */
    override fun savePushToken(provider: String, value: String) {
        saveValue(StorageKeys.pushToken(provider), value)
        device?.savePushToken(provider, value)
    }

    override fun getLastPushTokenDate(provider: String): Long {
        val value = getValue(StorageKeys.pushTokenDate(provider), DEFAULT_LAST_PUSH_TOKEN_DATE)
        if (value != DEFAULT_LAST_PUSH_TOKEN_DATE) return value
        val legacyKey = StorageKeys.LEGACY_PUSH_TOKEN_DATE[provider] ?: return value
        return getValue(legacyKey, DEFAULT_LAST_PUSH_TOKEN_DATE)
    }

    override fun saveLastPushTokenDate(provider: String, value: Long) =
        saveValue(StorageKeys.pushTokenDate(provider), value)

    override fun getValue(field: String, defaultValue: String): String {
        return sharedPreferences?.getString(field, defaultValue) ?: defaultValue
    }

    override fun getValue(field: String, defaultValue: Long): Long {
        return sharedPreferences?.getLong(field, defaultValue) ?: defaultValue
    }

    /**
     * Writes [value] under [field]. The `did` is committed before this returns: the server handed it
     * out once, and a crash before a background write reached the disk would make the next launch a
     * new device. Everything else is written in the background.
     */
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
        } ?: return

        if (field != StorageKeys.DID) {
            putEditor.apply()
        } else if (!putEditor.commit()) {
            SdkLogs.log(LogLevel.WARNING, LogCodes.STORAGE_FAILED, "Storage operation failed: $OP_WRITE_DID", shopId, mapOf("op" to OP_WRITE_DID))
        }
    }

    override fun removeValue(field: String) {
        sharedPreferences?.edit()?.remove(field)?.apply()
    }

    private companion object {
        const val OP_WRITE_DID = "write_did"
    }
}
