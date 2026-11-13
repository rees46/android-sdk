package com.personalization.core.internal

import com.personalization.PreferencesPartition

/**
 * Where a shop's data lives, and where the data of older releases is read from.
 *
 * The partition is the shop's own `SharedPreferences` file: `personalization_sdk_<shopId>`, or with a
 * [storageKey] `<storageKey>_personalization_sdk_<shopId>`. Its files (the saved `/init` answer, the
 * queue) live in a directory of the same name under `noBackupFilesDir`.
 *
 * Older releases are read once into a fresh partition ([StorageMigrations]):
 * - with a [storageKey], the file named exactly that: what `SDK.initialize(preferencesKey = …)` used
 *   as the whole store, one file with no per-shop partition;
 * - without one, the file shared by every shop before partitions ([PreferencesPartition.LEGACY_KEY]).
 */
internal class StorageLayout(val shopId: String, storageKey: String?) {

    /** The app's prefix, or null for the SDK's default layout. */
    val prefix: String? = storageKey?.takeIf { it.isNotBlank() && it != PreferencesPartition.LEGACY_KEY }

    /** The name of the shop's `SharedPreferences` file. */
    val partitionName: String =
        prefix?.let { PreferencesPartition.sanitize(it) + "_" + PreferencesPartition.keyFor(shopId) } ?: PreferencesPartition.keyFor(shopId)

    /** The old full-name file, read once into the partition. */
    val namedFile: String? = prefix?.takeIf { it != partitionName }

    /** The file of the releases before partitions, read once into a default partition. */
    val legacyFile: String? = if (prefix == null) PreferencesPartition.LEGACY_KEY else null
}
