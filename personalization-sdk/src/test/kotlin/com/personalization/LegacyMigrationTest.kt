package com.personalization

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Method

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class LegacyMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private companion object {
        const val LEGACY_KEY = "DEFAULT_STORAGE_KEY"
        const val PREFS_PREFIX = "sdk_prefs_"
    }

    @Before
    fun setUp() {
        clearPrefs(LEGACY_KEY)
        clearPrefs("${PREFS_PREFIX}shop_A")
        clearPrefs("${PREFS_PREFIX}shop_B")
        clearInstances()
    }

    @After
    fun tearDown() {
        clearInstances()
    }

    @Test
    fun migration_legacyDataExists_firstInstance_dataMigrated() {
        writeLegacyData("test_did", "test_sid", "test_token")

        callMigrateIfNeeded(context, "${PREFS_PREFIX}shop_A")

        val newPrefs = context.getSharedPreferences("${PREFS_PREFIX}shop_A", Context.MODE_PRIVATE)
        assertEquals("test_did", newPrefs.getString("did", ""))
        assertEquals("test_sid", newPrefs.getString("sid", ""))
        assertEquals("test_token", newPrefs.getString("token", ""))
    }

    @Test
    fun migration_allKeysAreMigrated() {
        val legacyPrefs = context.getSharedPreferences(LEGACY_KEY, Context.MODE_PRIVATE)
        legacyPrefs.edit()
            .putString("did", "device123")
            .putString("sid", "session456")
            .putLong("sid_last_act", 1700000000L)
            .putString("shop_id", "old_shop")
            .putString("stream", "android")
            .putString("segment_ab_testing", "A")
            .putString("google_ad_id", "ad_id_789")
            .putString("token", "fcm_token")
            .putLong("last_push_token_date", 1699000000L)
            .putString("source_type", "push")
            .putString("source_id", "src_123")
            .putString("source_time", "2024-01-01")
            .apply()

        callMigrateIfNeeded(context, "${PREFS_PREFIX}shop_A")

        val newPrefs = context.getSharedPreferences("${PREFS_PREFIX}shop_A", Context.MODE_PRIVATE)
        assertEquals("device123", newPrefs.getString("did", ""))
        assertEquals("session456", newPrefs.getString("sid", ""))
        assertEquals(1700000000L, newPrefs.getLong("sid_last_act", 0L))
        assertEquals("old_shop", newPrefs.getString("shop_id", ""))
        assertEquals("android", newPrefs.getString("stream", ""))
        assertEquals("A", newPrefs.getString("segment_ab_testing", ""))
        assertEquals("ad_id_789", newPrefs.getString("google_ad_id", ""))
        assertEquals("fcm_token", newPrefs.getString("token", ""))
        assertEquals(1699000000L, newPrefs.getLong("last_push_token_date", 0L))
        assertEquals("push", newPrefs.getString("source_type", ""))
        assertEquals("src_123", newPrefs.getString("source_id", ""))
        assertEquals("2024-01-01", newPrefs.getString("source_time", ""))
    }

    @Test
    fun migration_secondInstance_doesNotGetLegacyData() {
        writeLegacyData("test_did", "test_sid", "test_token")

        callMigrateIfNeeded(context, "${PREFS_PREFIX}shop_A")
        registerFakeInstance("shop_A")

        callMigrateIfNeeded(context, "${PREFS_PREFIX}shop_B")

        val newPrefsB = context.getSharedPreferences("${PREFS_PREFIX}shop_B", Context.MODE_PRIVATE)
        assertFalse(newPrefsB.contains("did"))
    }

    @Test
    fun migration_noLegacyData_freshInstall_noCrash() {
        callMigrateIfNeeded(context, "${PREFS_PREFIX}shop_A")

        val newPrefs = context.getSharedPreferences("${PREFS_PREFIX}shop_A", Context.MODE_PRIVATE)
        assertFalse(newPrefs.contains("did"))
    }

    @Test
    fun migration_idempotent_runInitTwice_noDataDuplication() {
        writeLegacyData("original_did", "original_sid", "original_token")

        callMigrateIfNeeded(context, "${PREFS_PREFIX}shop_A")

        val newPrefs = context.getSharedPreferences("${PREFS_PREFIX}shop_A", Context.MODE_PRIVATE)
        newPrefs.edit().putString("did", "updated_did").apply()

        callMigrateIfNeeded(context, "${PREFS_PREFIX}shop_A")

        assertEquals("updated_did", newPrefs.getString("did", ""))
    }

    @Test
    fun migration_newPrefsAlreadyHasData_skipsMigration() {
        writeLegacyData("legacy_did", "legacy_sid", "legacy_token")

        val newPrefs = context.getSharedPreferences("${PREFS_PREFIX}shop_A", Context.MODE_PRIVATE)
        newPrefs.edit()
            .putString("did", "existing_did")
            .putString("token", "existing_token")
            .apply()

        callMigrateIfNeeded(context, "${PREFS_PREFIX}shop_A")

        assertEquals("existing_did", newPrefs.getString("did", ""))
        assertEquals("existing_token", newPrefs.getString("token", ""))
    }

    @Test
    fun migration_legacyPrefsNotDeleted() {
        writeLegacyData("test_did", "test_sid", "test_token")

        callMigrateIfNeeded(context, "${PREFS_PREFIX}shop_A")

        val legacyPrefs = context.getSharedPreferences(LEGACY_KEY, Context.MODE_PRIVATE)
        assertEquals("test_did", legacyPrefs.getString("did", ""))
        assertEquals("test_sid", legacyPrefs.getString("sid", ""))
        assertEquals("test_token", legacyPrefs.getString("token", ""))
    }

    @Test
    fun migration_longValues_preserveType() {
        val legacyPrefs = context.getSharedPreferences(LEGACY_KEY, Context.MODE_PRIVATE)
        legacyPrefs.edit()
            .putString("did", "device")
            .putLong("last_push_token_date", 1700000000L)
            .putLong("sid_last_act", 1699999999L)
            .apply()

        callMigrateIfNeeded(context, "${PREFS_PREFIX}shop_A")

        val newPrefs = context.getSharedPreferences("${PREFS_PREFIX}shop_A", Context.MODE_PRIVATE)
        assertTrue(newPrefs.all["last_push_token_date"] is Long)
        assertTrue(newPrefs.all["sid_last_act"] is Long)
        assertEquals(1700000000L, newPrefs.getLong("last_push_token_date", 0L))
        assertEquals(1699999999L, newPrefs.getLong("sid_last_act", 0L))
    }

    private fun writeLegacyData(did: String, sid: String, token: String) {
        context.getSharedPreferences(LEGACY_KEY, Context.MODE_PRIVATE)
            .edit()
            .putString("did", did)
            .putString("sid", sid)
            .putString("token", token)
            .apply()
    }

    private fun callMigrateIfNeeded(context: Context, effectiveKey: String) {
        val sdk = SDK()
        val method: Method = SDK::class.java.getDeclaredMethod(
            "migrateIfNeeded",
            Context::class.java,
            String::class.java
        )
        method.isAccessible = true
        method.invoke(sdk, context, effectiveKey)
    }

    private fun registerFakeInstance(shopId: String) {
        val instancesField = SDK::class.java.getDeclaredField("instances")
        instancesField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val instances = instancesField.get(null) as java.util.concurrent.ConcurrentHashMap<String, SDK>
        instances[shopId] = SDK()
    }

    private fun clearInstances() {
        SDK.getAllInstances().keys.toList().forEach { SDK.destroyInstance(it) }
    }

    private fun clearPrefs(key: String) {
        context.getSharedPreferences(key, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
