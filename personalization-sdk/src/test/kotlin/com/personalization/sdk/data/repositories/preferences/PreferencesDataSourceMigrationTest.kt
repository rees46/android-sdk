package com.personalization.sdk.data.repositories.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.personalization.PreferencesPartition
import com.personalization.core.internal.StorageLayout
import com.personalization.core.internal.StorageMigrations
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers the one-time legacy migration in [PreferencesDataSourceImpl] (Release 2). Uses real
 * Robolectric SharedPreferences: the migration copies between two files, so mocking the store would
 * defeat the point.
 *
 * The legacy file is the pre-partitioning shared store ([PreferencesPartition.LEGACY_KEY]); the
 * target is a shop partition. An existing single-instance install must keep its did/sid after the
 * upgrade, while a partition for a different shop must stay clean.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PreferencesDataSourceMigrationTest {

    private lateinit var context: Context

    private val legacyKey = PreferencesPartition.LEGACY_KEY
    private val shopId = "shop-a"
    private val partitionKey = PreferencesPartition.keyFor(shopId)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Robolectric keeps SharedPreferences between tests in the same process — clear both files.
        context.getSharedPreferences(legacyKey, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences(partitionKey, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences(PreferencesPartition.keyFor("shop-b"), Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    private fun seedLegacy(vararg entries: Pair<String, String>) {
        val editor = context.getSharedPreferences(legacyKey, Context.MODE_PRIVATE).edit()
        entries.forEach { (k, v) -> editor.putString(k, v) }
        editor.commit()
    }

    /** What a start does: the migrations, then the partition opened. */
    private fun initPartition(shop: String = shopId): PreferencesDataSourceImpl {
        StorageMigrations.run(context, StorageLayout(shop, null))
        return PreferencesDataSourceImpl().apply {
            initialize(
                context = context,
                preferencesKey = PreferencesPartition.keyFor(shop),
                shopId = shop
            )
        }
    }

    @Test
    fun `an existing install keeps its did and sid after upgrading to a partition`() {
        seedLegacy(
            PreferencesPartition.SHOP_ID_FIELD to shopId,
            "did" to "device-123",
            "sid" to "seance-456"
        )

        val source = initPartition()

        assertEquals("device-123", source.getValue("did", ""))
        assertEquals("seance-456", source.getValue("sid", ""))
    }

    @Test
    fun `a partition for a different shop is not filled from the legacy file`() {
        seedLegacy(
            PreferencesPartition.SHOP_ID_FIELD to shopId,
            "did" to "device-123"
        )

        val other = initPartition(shop = "shop-b")

        assertEquals("", other.getValue("did", ""))
    }

    @Test
    fun `nothing is migrated when there is no legacy data`() {
        val source = initPartition()

        assertEquals("", source.getValue("did", ""))
    }

    @Test
    fun `migration does not overwrite data already in the partition`() {
        // Partition already holds a did (SDK ran under Release 2 before); legacy still has an old one.
        context.getSharedPreferences(partitionKey, Context.MODE_PRIVATE)
            .edit().putString("did", "current-partition-did").commit()
        seedLegacy(
            PreferencesPartition.SHOP_ID_FIELD to shopId,
            "did" to "stale-legacy-did"
        )

        val source = initPartition()

        assertEquals("current-partition-did", source.getValue("did", ""))
    }

    @Test
    fun `a legacy file without a shop id is still migrated for a very old install`() {
        // Pre-shop-id installs stored no shop_id; there is only one shop, so bring it over.
        seedLegacy("did" to "legacy-no-shop")

        val source = initPartition()

        assertEquals("legacy-no-shop", source.getValue("did", ""))
    }

    @Test
    fun `a custom preferences key skips migration entirely`() {
        seedLegacy(
            PreferencesPartition.SHOP_ID_FIELD to shopId,
            "did" to "device-123"
        )

        val source = PreferencesDataSourceImpl().apply {
            // A partition opened by name without a start's migrations reads nothing older.
            initialize(context = context, preferencesKey = "host_custom_prefs")
        }

        assertEquals("", source.getValue("did", ""))
    }
}
