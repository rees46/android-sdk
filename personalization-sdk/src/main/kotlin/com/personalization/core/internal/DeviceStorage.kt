package com.personalization.core.internal

import android.content.Context
import android.content.SharedPreferences

/**
 * What belongs to the device rather than to a shop or a customer: the A/B group, the push tokens and
 * the advertising id. One file for the app, shared by every shop and every [StorageLayout.prefix].
 *
 * Each shop's partition keeps its own copy under the keys the legacy path reads ([StorageKeys]), so
 * nothing the legacy path reads changes: the device level is written beside it.
 *
 * The A/B group is the first shop's: a device has one group, and the group a shop already sends keeps
 * being sent ([adoptSegment]).
 */
internal class DeviceStorage(context: Context) {

    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** The device's A/B group, or null before any shop had one. */
    fun segment(): String? = preferences.getString(SEGMENT, null)?.takeIf { it.isNotEmpty() }

    /**
     * The device's A/B group: the one it has, or [candidate] if it has none yet. The first caller in
     * the process wins, so two shops starting together agree on one group.
     */
    fun adoptSegment(candidate: String): String = synchronized(LOCK) {
        segment() ?: candidate.also { preferences.edit().putString(SEGMENT, it).apply() }
    }

    /** The last push token of [provider] (`fcm`, `hms`) the device has, or null. */
    fun pushToken(provider: String): String? = preferences.getString(StorageKeys.pushToken(provider), null)?.takeIf { it.isNotEmpty() }

    fun savePushToken(provider: String, token: String) {
        if (token.isEmpty()) return
        preferences.edit().putString(StorageKeys.pushToken(provider), token).apply()
    }

    /** Takes [token] only while the device has no token of [provider]: a newer one is never replaced by a copy. */
    fun adoptPushToken(provider: String, token: String) = synchronized(LOCK) {
        if (pushToken(provider) == null) savePushToken(provider, token)
    }

    /** The advertising id the device last reported, or null. */
    fun advertisingId(): String? = preferences.getString(StorageKeys.ADVERTISING_ID, null)?.takeIf { it.isNotEmpty() }

    fun saveAdvertisingId(id: String) {
        if (id.isEmpty()) return
        preferences.edit().putString(StorageKeys.ADVERTISING_ID, id).apply()
    }

    /** Takes [id] only while the device has none. */
    fun adoptAdvertisingId(id: String) = synchronized(LOCK) {
        if (advertisingId() == null) saveAdvertisingId(id)
    }

    internal companion object {
        /**
         * The file of the device level. The dot keeps it apart from every partition: a shop id's dots
         * become underscores in a partition's name.
         */
        const val NAME = "personalization_sdk.device"

        const val SEGMENT = StorageKeys.SEGMENT

        /** The two groups of the server's A/B tests: the only values the SDK gives a device. */
        val SEGMENTS: List<String> = listOf("A", "B")

        /** One lock for the process: the instances of every shop share the file. */
        private val LOCK = Any()
    }
}
