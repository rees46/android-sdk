package com.personalization.core.internal

/**
 * The keys of a shop's partition, in one place: the legacy path reads and writes them, the migrations
 * copy them and [ShopStorage.deleteUserCredentials] knows which of them belong to the customer.
 *
 * Every key that has shipped keeps its name and meaning: an installed app has data under it.
 */
internal object StorageKeys {

    // What the shipped SDK stores per shop. `LegacyCore` reads these.
    const val DID = "did"
    const val SID = "sid"
    const val SID_LAST_ACT = "sid_last_act"
    const val SHOP_ID = "shop_id"
    const val SEGMENT = "segment_ab_testing"
    const val STREAM = "stream"
    const val ADVERTISING_ID = "google_ad_id"
    const val TRACKING_SOURCE_TYPE = "tracking_source_type"
    const val TRACKING_SOURCE_CODE = "tracking_source_code"
    const val TRACKING_SOURCE_SAVED_AT = "tracking_source_saved_at"

    /** The push token of a provider (`fcm`, `hms`) as last sent from this shop. */
    fun pushToken(provider: String): String = "push_token_$provider"

    /** When the shop last sent the push token of a provider. */
    fun pushTokenDate(provider: String): String = "push_token_date_$provider"

    /** The token keys of releases before the provider was part of the key; still read as a fallback. */
    val LEGACY_PUSH_TOKEN: Map<String, String> = mapOf("fcm" to "token", "hms" to "hms_token")

    /** The send-date keys of releases before the provider was part of the key; still read as a fallback. */
    val LEGACY_PUSH_TOKEN_DATE: Map<String, String> = mapOf("fcm" to "last_push_token_date", "hms" to "last_hms_push_token_date")

    /** The providers whose token the SDK stores. */
    val PUSH_PROVIDERS: List<String> = listOf("fcm", "hms")

    // The core's own state.
    /** The shop refused the SDK (`402`, `403`, `404` or another `4xx` on `/init`). */
    const val REFUSED = "refused"

    // One-shot migrations: each marks itself here once it has run, so it never runs twice for a shop.
    /** The old full-name file (`preferencesKey` of `SDK.initialize`) was read into the partition. */
    const val MIGRATED_NAMED_FILE = "migrated_named_file"

    /** The shared file of the releases before per-shop partitions was read into the partition. */
    const val MIGRATED_LEGACY_FILE = "migrated_legacy_file"

    /** The shop's A/B group, push tokens and advertising id were offered to the device-level store. */
    const val MIGRATED_DEVICE_LEVEL = "migrated_device_level"

    val MIGRATION_FLAGS: Set<String> = setOf(MIGRATED_NAMED_FILE, MIGRATED_LEGACY_FILE, MIGRATED_DEVICE_LEVEL)

    /**
     * What `deleteUserCredentials()` wipes from the partition: the customer's identity and session,
     * the source `tracking.setSource` set, the dates the push token was sent (so the token goes to the
     * next customer), and the refused state. The A/B group, the push tokens, the advertising id, the
     * stream, the shop id and the migration flags stay: they belong to the device or the app.
     */
    val USER_CREDENTIALS: List<String> = buildList {
        add(DID)
        add(SID)
        add(SID_LAST_ACT)
        add(TRACKING_SOURCE_TYPE)
        add(TRACKING_SOURCE_CODE)
        add(TRACKING_SOURCE_SAVED_AT)
        PUSH_PROVIDERS.forEach { add(pushTokenDate(it)) }
        addAll(LEGACY_PUSH_TOKEN_DATE.values)
        add(REFUSED)
    }
}
