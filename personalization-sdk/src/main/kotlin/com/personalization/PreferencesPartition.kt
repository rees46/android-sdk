package com.personalization

/**
 * Naming of the SDK's SharedPreferences partitions.
 *
 * Release 2 of multi-instance: storage is partitioned per `shop_id` so instances do not share one
 * file. Transparent to hosts — an existing install's data is read once into the shop's partition,
 * so it keeps its `did`/`sid`. With an app's `storageKey` (formerly `SDK.initialize(preferencesKey)`)
 * the partition name gets that prefix; see [com.personalization.core.internal.StorageLayout].
 */
internal object PreferencesPartition {

    /**
     * The single, shared SharedPreferences file used before per-shop partitioning, and the default
     * value of `SDK.initialize(preferencesKey)`. When a host leaves the default, this file is the
     * migration source for the shop's partition.
     */
    const val LEGACY_KEY = "DEFAULT_STORAGE_KEY"

    /**
     * Stored field holding the shop id. The migration only pulls the legacy file into the partition
     * of the shop it actually belongs to. Must match `UserSettingsRepositoryImpl.SHOP_ID_KEY`.
     */
    const val SHOP_ID_FIELD = "shop_id"

    private const val PREFIX = "personalization_sdk_"
    private val UNSAFE_FILENAME_CHARS = Regex("[^A-Za-z0-9_-]")

    /**
     * Stable, filename-safe SharedPreferences name for [shopId]'s partition. Stable forever:
     * changing the scheme would orphan every existing install's storage.
     */
    fun keyFor(shopId: String): String = PREFIX + sanitize(shopId)

    /** [value] as a part of a file name: every character but letters, digits, `_` and `-` becomes `_`. */
    fun sanitize(value: String): String = value.replace(UNSAFE_FILENAME_CHARS, "_")
}
