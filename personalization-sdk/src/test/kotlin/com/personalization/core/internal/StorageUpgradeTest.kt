package com.personalization.core.internal

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.personalization.PreferencesPartition
import com.personalization.sdk.data.repositories.preferences.PreferencesDataSourceImpl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Upgrades from the layouts that installed apps have on disk: the 2.4x file shared by every shop, with
 * and without a shop in it; a per-shop partition of a later release; the whole store of a custom
 * `preferencesKey`. Real Robolectric `SharedPreferences`: the migrations copy between files.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class StorageUpgradeTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Robolectric keeps SharedPreferences between tests in the same process.
        (listOf(PreferencesPartition.LEGACY_KEY, HOST_KEY, DeviceStorage.NAME, StorageLayout(SHOP_A, "dir/prefs").partitionName) +
            listOf(SHOP_A, SHOP_B, SHOP_C).flatMap { listOf(StorageLayout(it, null).partitionName, StorageLayout(it, HOST_KEY).partitionName) })
            .forEach { prefs(it).edit().clear().commit() }
    }

    private fun prefs(name: String): SharedPreferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    private fun partition(shop: String, storageKey: String? = null) = prefs(StorageLayout(shop, storageKey).partitionName)

    private fun start(shop: String, storageKey: String? = null) = StorageMigrations.run(context, StorageLayout(shop, storageKey))

    /** What a 2.4x install wrote to its one file; without [shop] the file of an install older still. */
    private fun seed24x(name: String = PreferencesPartition.LEGACY_KEY, shop: String? = SHOP_A, did: String = "did-24x") {
        prefs(name).edit().apply {
            shop?.let { putString("shop_id", it) }
            putString("did", did)
            putString("sid", "sid-24x")
            putLong("sid_last_act", 1_600_000_000_000L)
            putString("segment_ab_testing", "B")
            putString("stream", "android")
            putString("google_ad_id", "ad-24x")
            putString("token", "fcm-24x")
            putLong("last_push_token_date", 1_600_000_000_001L)
            putString("tracking_source_type", "bulk")
        }.commit()
    }

    @Test
    fun `a 2_4x file of the shop moves into its partition and stays where it was`() {
        seed24x()

        start(SHOP_A)

        val partition = partition(SHOP_A)
        assertEquals("did-24x", partition.getString("did", null))
        assertEquals("sid-24x", partition.getString("sid", null))
        assertEquals(1_600_000_000_000L, partition.getLong("sid_last_act", 0))
        assertEquals("fcm-24x", partition.getString("token", null))
        assertEquals(1_600_000_000_001L, partition.getLong("last_push_token_date", 0))
        assertEquals("bulk", partition.getString("tracking_source_type", null))
        assertTrue(partition.getBoolean(StorageKeys.MIGRATED_LEGACY_FILE, false))
        // The source is only read.
        assertEquals("did-24x", prefs(PreferencesPartition.LEGACY_KEY).getString("did", null))
        assertEquals(SHOP_A, prefs(PreferencesPartition.LEGACY_KEY).getString("shop_id", null))
    }

    @Test
    fun `a 2_4x file of another shop is left out`() {
        seed24x(shop = SHOP_A)

        start(SHOP_B)

        val partition = partition(SHOP_B)
        assertNull(partition.getString("did", null))
        assertTrue(partition.getBoolean(StorageKeys.MIGRATED_LEGACY_FILE, false))
    }

    @Test
    fun `a 2_4x file without a shop goes to each shop that starts empty, and to each at most once`() {
        seed24x(shop = null)

        start(SHOP_A)
        start(SHOP_B)

        // As before: such a file has no owner, so every shop starting empty takes it.
        assertEquals("did-24x", partition(SHOP_A).getString("did", null))
        assertEquals("did-24x", partition(SHOP_B).getString("did", null))

        // The customer of shop A is forgotten; the old file still has its did and is never taken again.
        ShopStorage(context, StorageLayout(SHOP_A, null)).deleteUserCredentials(includeQueue = false)
        partition(SHOP_A).edit().apply { partition(SHOP_A).all.keys.filterNot { it in StorageKeys.MIGRATION_FLAGS }.forEach { remove(it) } }.commit()
        start(SHOP_A)

        assertNull(partition(SHOP_A).getString("did", null))
        assertEquals("did-24x", partition(SHOP_B).getString("did", null))
    }

    @Test
    fun `an existing partition keeps its data and the old file is not read into it`() {
        partition(SHOP_A).edit().putString("did", "did-partition").putString("shop_id", SHOP_A).putString("segment_ab_testing", "A").commit()
        seed24x(shop = SHOP_A, did = "did-stale")

        start(SHOP_A)

        val partition = partition(SHOP_A)
        assertEquals("did-partition", partition.getString("did", null))
        assertNull(partition.getString("sid", null))
        assertEquals("A", partition.getString("segment_ab_testing", null))
        assertTrue(partition.getBoolean(StorageKeys.MIGRATED_LEGACY_FILE, false))
    }

    @Test
    fun `a custom preferencesKey file moves into the prefixed partition, whatever shop it last named`() {
        // Two shops shared the host's one file; the last to start wrote its shop id there.
        seed24x(name = HOST_KEY, shop = SHOP_B, did = "did-host")
        seed24x(shop = SHOP_A, did = "did-legacy")

        start(SHOP_A, HOST_KEY)
        start(SHOP_B, HOST_KEY)

        assertEquals("host_prefs_personalization_sdk_shop-a", StorageLayout(SHOP_A, HOST_KEY).partitionName)
        // Each keeps the did it read before: shops that shared it go on sharing it, nothing worse.
        assertEquals("did-host", partition(SHOP_A, HOST_KEY).getString("did", null))
        assertEquals("did-host", partition(SHOP_B, HOST_KEY).getString("did", null))
        assertTrue(partition(SHOP_A, HOST_KEY).getBoolean(StorageKeys.MIGRATED_NAMED_FILE, false))
        // The prefixed layout does not read the file all shops shared before partitions.
        assertFalse(partition(SHOP_A, HOST_KEY).contains(StorageKeys.MIGRATED_LEGACY_FILE))
        // The host's file is left as it was.
        assertEquals("did-host", prefs(HOST_KEY).getString("did", null))
        assertEquals(SHOP_B, prefs(HOST_KEY).getString("shop_id", null))
    }

    @Test
    fun `a second start repeats no migration`() {
        seed24x(shop = SHOP_A)
        start(SHOP_A)
        val device = DeviceStorage(context)
        val first = partition(SHOP_A).all

        // The sources change after the first start (nothing writes them any more, but a migration must not care).
        seed24x(shop = SHOP_A, did = "did-changed")
        prefs(DeviceStorage.NAME).edit().remove(StorageKeys.pushToken("fcm")).commit()
        start(SHOP_A)

        assertEquals(first, partition(SHOP_A).all)
        assertNull(device.pushToken("fcm"))
    }

    @Test
    fun `a storageKey gives the shop a partition of its own, apart from the default one`() {
        partition(SHOP_A).edit().putString("did", "did-default").commit()

        start(SHOP_A, HOST_KEY)

        assertNull(partition(SHOP_A, HOST_KEY).getString("did", null))
        assertEquals("did-default", partition(SHOP_A).getString("did", null))
    }

    @Test
    fun `the layout names the partition, its old files and its directory`() {
        val default = StorageLayout("357382bf/66.ac", null)
        assertEquals("personalization_sdk_357382bf_66_ac", default.partitionName)
        assertEquals(PreferencesPartition.LEGACY_KEY, default.legacyFile)
        assertNull(default.namedFile)

        val prefixed = StorageLayout("shop", "com.host/app")
        assertEquals("com_host_app_personalization_sdk_shop", prefixed.partitionName)
        assertEquals("com.host/app", prefixed.namedFile)
        assertNull(prefixed.legacyFile)

        // The default of SDK.initialize(preferencesKey) and a blank key are no prefix.
        assertEquals(default.partitionName, StorageLayout("357382bf/66.ac", PreferencesPartition.LEGACY_KEY).partitionName)
        assertEquals("personalization_sdk_shop", StorageLayout("shop", " ").partitionName)
    }

    @Test
    fun `a custom key that cannot name a file costs its migration, not the start`() {
        start(SHOP_A, "dir/prefs")

        assertTrue(partition(SHOP_A, "dir/prefs").getBoolean(StorageKeys.MIGRATED_NAMED_FILE, false))
        assertTrue(partition(SHOP_A, "dir/prefs").getBoolean(StorageKeys.MIGRATED_DEVICE_LEVEL, false))
    }

    @Test
    fun `the first shop's A_B group becomes the device's, and each shop keeps sending its own`() {
        // Two shops of a release with per-shop groups, which happened to differ.
        partition(SHOP_A).edit().putString("did", "a").putString("segment_ab_testing", "B").commit()
        partition(SHOP_B).edit().putString("did", "b").putString("segment_ab_testing", "A").commit()

        start(SHOP_A)
        start(SHOP_B)

        assertEquals("B", DeviceStorage(context).segment())
        assertEquals("B", partition(SHOP_A).getString("segment_ab_testing", null))
        assertEquals("A", partition(SHOP_B).getString("segment_ab_testing", null))
    }

    @Test
    fun `a new shop takes the device's group, and a device without one gets it from its first shop`() {
        start(SHOP_A)
        val group = DeviceStorage(context).segment()
        assertTrue(group == "A" || group == "B")
        assertEquals(group, partition(SHOP_A).getString("segment_ab_testing", null))

        repeat(2) { start(SHOP_B) }
        start(SHOP_C)

        assertEquals(group, partition(SHOP_B).getString("segment_ab_testing", null))
        assertEquals(group, partition(SHOP_C).getString("segment_ab_testing", null))
        assertEquals(group, DeviceStorage(context).segment())
    }

    @Test
    fun `the shops' push tokens and advertising id go to the device, the first one kept`() {
        partition(SHOP_A).edit()
            .putString("token", "fcm-old-key")
            .putString(StorageKeys.pushToken("hms"), "hms-a")
            .putString("google_ad_id", "ad-a")
            .commit()
        partition(SHOP_B).edit()
            .putString(StorageKeys.pushToken("fcm"), "fcm-b")
            .putString("google_ad_id", "ad-b")
            .commit()

        start(SHOP_A)
        start(SHOP_B)

        val device = DeviceStorage(context)
        assertEquals("fcm-old-key", device.pushToken("fcm"))
        assertEquals("hms-a", device.pushToken("hms"))
        assertEquals("ad-a", device.advertisingId())
        // The shops' own copies are what the legacy path reads; they stay.
        assertEquals("fcm-b", partition(SHOP_B).getString(StorageKeys.pushToken("fcm"), null))
        assertEquals("ad-b", partition(SHOP_B).getString("google_ad_id", null))
    }

    @Test
    fun `a token sent later is written for the shop and for the device`() {
        start(SHOP_A)
        val source = PreferencesDataSourceImpl().apply {
            initialize(context = context, preferencesKey = StorageLayout(SHOP_A, null).partitionName, shopId = SHOP_A)
        }

        source.savePushToken("fcm", "fcm-new")

        assertEquals("fcm-new", source.getPushToken("fcm"))
        assertEquals("fcm-new", partition(SHOP_A).getString(StorageKeys.pushToken("fcm"), null))
        assertEquals("fcm-new", DeviceStorage(context).pushToken("fcm"))
    }

    private companion object {
        const val SHOP_A = "shop-a"
        const val SHOP_B = "shop-b"
        const val SHOP_C = "shop-c"
        const val HOST_KEY = "host_prefs"
    }
}
