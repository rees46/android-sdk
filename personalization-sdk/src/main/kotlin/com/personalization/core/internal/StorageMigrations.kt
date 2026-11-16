package com.personalization.core.internal

import android.content.Context
import android.content.SharedPreferences
import com.personalization.LogLevel
import com.personalization.logs.LogCodes
import com.personalization.logs.SdkLogs

/**
 * The one-shot migrations of a shop's partition, run on every start before `/init`. Each runs at most
 * once per shop: it marks itself with a flag in the partition ([StorageKeys.MIGRATION_FLAGS]), in the
 * same write as what it copied. The sources are only read here; `deleteUserCredentials()` later wipes
 * the customer's identity from them ([StorageKeys.SOURCE_CREDENTIALS]), so it cannot come back.
 *
 * 1. **The old full-name file** ([StorageLayout.namedFile]): what `SDK.initialize(preferencesKey = …)`
 *    used as the whole store. Read into a partition that has no data yet, whatever shop it last
 *    belonged to: two shops that shared such a file shared its `did`, and keep sharing it. Only the
 *    SDK's own keys ([StorageKeys.SDK_KEYS]) are taken: the app may have named its own preferences.
 * 2. **The file shared by every shop before partitions** ([StorageLayout.legacyFile]): read into a
 *    default partition that has no data yet, when it belongs to the shop or names no shop at all (a
 *    very old install). A file without a shop goes to each shop that starts with an empty partition,
 *    as before; the flag keeps a shop from taking it again after its data was deleted.
 * 3. **The device level** ([DeviceStorage]): the shop offers its A/B group, push tokens and advertising
 *    id, which the device takes while it has none of its own. A shop without an A/B group takes the
 *    device's, so every new shop of the device sends the same group.
 *
 * The writes go through `apply()`: the copy and its flag are one write, so a crash loses both or
 * neither, and the next start runs the migration again.
 */
internal object StorageMigrations {

    /** Runs what has not run yet for [layout]'s shop. A failure is logged and never stops the start. */
    fun run(context: Context, layout: StorageLayout, device: DeviceStorage = DeviceStorage(context)) {
        val shopId = layout.shopId
        val partition = open(context, layout.partitionName, shopId, OP_OPEN_PARTITION) ?: return
        layout.namedFile?.let { name ->
            step(shopId, OP_MIGRATE_NAMED_FILE) {
                copyOnce(context, name, partition, StorageKeys.MIGRATED_NAMED_FILE, shopId, keys = StorageKeys.SDK_KEYS, belongsTo = { true })
            }
        }
        layout.legacyFile?.let { name -> step(shopId, OP_MIGRATE_LEGACY_FILE) { migrateLegacyFile(context, name, partition, shopId) } }
        step(shopId, OP_MIGRATE_DEVICE_LEVEL) { adoptDeviceLevel(partition, device) }
    }

    private fun migrateLegacyFile(context: Context, legacyName: String, partition: SharedPreferences, shopId: String) {
        copyOnce(context, legacyName, partition, StorageKeys.MIGRATED_LEGACY_FILE, shopId, keys = null) { source -> belongsTo(source, shopId) }
    }

    /** Whether the store of releases before partitions is [shopId]'s: it names that shop, or no shop at all. */
    internal fun belongsTo(source: SharedPreferences, shopId: String): Boolean {
        val owner = source.getString(StorageKeys.SHOP_ID, "").orEmpty()
        return owner.isEmpty() || owner == shopId
    }

    /** Whether [partition] holds anything besides migration flags. */
    internal fun hasData(partition: SharedPreferences): Boolean =
        partition.all.keys.any { it !in StorageKeys.MIGRATION_FLAGS }

    private fun copyOnce(
        context: Context,
        sourceName: String,
        partition: SharedPreferences,
        flag: String,
        shopId: String,
        /** The keys to take; null takes the whole file, the SDK's own. */
        keys: Set<String>?,
        belongsTo: (SharedPreferences) -> Boolean,
    ) {
        if (partition.getBoolean(flag, false)) return
        val editor = partition.edit()
        // A partition that already has data keeps it: it is newer than any file of an older release.
        if (!hasData(partition)) {
            val source = open(context, sourceName, shopId, OP_OPEN_SOURCE)
            if (source != null && belongsTo(source)) {
                source.all.forEach { (key, value) ->
                    if (key !in StorageKeys.MIGRATION_FLAGS && (keys == null || key in keys)) editor.putAny(key, value)
                }
            }
        }
        editor.putBoolean(flag, true).apply()
    }

    private fun adoptDeviceLevel(partition: SharedPreferences, device: DeviceStorage) {
        if (partition.getBoolean(StorageKeys.MIGRATED_DEVICE_LEVEL, false)) return
        val editor = partition.edit()
        val segment = partition.getString(StorageKeys.SEGMENT, null)?.takeIf { it.isNotEmpty() }
        if (segment != null) {
            // The shop keeps sending its own group; the device takes it if it is the first.
            device.adoptSegment(segment)
        } else {
            editor.putString(StorageKeys.SEGMENT, device.adoptSegment(randomSegment()))
        }
        StorageKeys.PUSH_PROVIDERS.forEach { provider ->
            val token = partition.getString(StorageKeys.pushToken(provider), null)?.takeIf { it.isNotEmpty() }
                ?: StorageKeys.LEGACY_PUSH_TOKEN[provider]?.let { partition.getString(it, null) }?.takeIf { it.isNotEmpty() }
            token?.let { device.adoptPushToken(provider, it) }
        }
        partition.getString(StorageKeys.ADVERTISING_ID, null)?.takeIf { it.isNotEmpty() }?.let { device.adoptAdvertisingId(it) }
        editor.putBoolean(StorageKeys.MIGRATED_DEVICE_LEVEL, true).apply()
    }

    /** The two groups of the server's A/B tests, picked at random once per device. */
    internal fun randomSegment(): String = arrayOf("A", "B").random()

    private fun open(context: Context, name: String, shopId: String, op: String): SharedPreferences? = try {
        context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)
    } catch (e: Exception) {
        // A name with a path separator, an unreadable file: the start goes on without it.
        failed(shopId, op, e)
        null
    }

    private inline fun step(shopId: String, op: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            failed(shopId, op, e)
        }
    }

    private fun failed(shopId: String, op: String, throwable: Throwable) {
        SdkLogs.log(LogLevel.WARNING, LogCodes.STORAGE_FAILED, "Storage operation failed: $op", shopId, mapOf("op" to op), throwable)
    }

    private fun SharedPreferences.Editor.putAny(key: String, value: Any?) {
        when (value) {
            is String -> putString(key, value)
            is Long -> putLong(key, value)
            is Int -> putInt(key, value)
            is Float -> putFloat(key, value)
            is Boolean -> putBoolean(key, value)
            is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
        }
    }

    const val OP_OPEN_PARTITION = "open_partition"
    const val OP_OPEN_SOURCE = "open_old_storage"
    const val OP_MIGRATE_NAMED_FILE = "migrate_named_file"
    const val OP_MIGRATE_LEGACY_FILE = "migrate_legacy_file"
    const val OP_MIGRATE_DEVICE_LEVEL = "migrate_device_level"
}
