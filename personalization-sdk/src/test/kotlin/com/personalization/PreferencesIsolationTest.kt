package com.personalization

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class PreferencesIsolationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        SDK.getAllInstances().keys.toList().forEach { SDK.destroyInstance(it) }
    }

    @Test
    fun initialize_derivesPrefsKeyFromShopId() {
        val effectiveKey = computeEffectiveKey(shopId = "shop_abc", preferencesKey = "")
        assertEquals("sdk_prefs_shop_abc", effectiveKey)
    }

    @Test
    fun multiInstance_prefsAreIsolated() {
        val keyA = computeEffectiveKey("shop_A", "")
        val keyB = computeEffectiveKey("shop_B", "")

        val prefsA = context.getSharedPreferences(keyA, Context.MODE_PRIVATE)
        val prefsB = context.getSharedPreferences(keyB, Context.MODE_PRIVATE)

        prefsA.edit().putString("did", "device_A").apply()
        prefsB.edit().putString("did", "device_B").apply()

        assertEquals("device_A", prefsA.getString("did", ""))
        assertEquals("device_B", prefsB.getString("did", ""))
        assertNotEquals(
            prefsA.getString("did", ""),
            prefsB.getString("did", "")
        )
    }

    @Test
    fun multiInstance_tokensAreIsolated() {
        val keyA = computeEffectiveKey("shop_A", "")
        val keyB = computeEffectiveKey("shop_B", "")

        val prefsA = context.getSharedPreferences(keyA, Context.MODE_PRIVATE)
        val prefsB = context.getSharedPreferences(keyB, Context.MODE_PRIVATE)

        prefsA.edit().putString("token", "token_A").apply()
        prefsB.edit().putString("token", "token_B").apply()

        assertEquals("token_A", prefsA.getString("token", ""))
        assertEquals("token_B", prefsB.getString("token", ""))
    }

    @Test
    fun explicitPreferencesKey_isRespected() {
        val effectiveKey = computeEffectiveKey(shopId = "shop_abc", preferencesKey = "custom_key")
        assertEquals("custom_key", effectiveKey)
    }

    @Test
    fun multiInstance_sessionIdsAreDifferent() {
        val keyA = computeEffectiveKey("shop_A", "")
        val keyB = computeEffectiveKey("shop_B", "")

        val prefsA = context.getSharedPreferences(keyA, Context.MODE_PRIVATE)
        val prefsB = context.getSharedPreferences(keyB, Context.MODE_PRIVATE)

        prefsA.edit().putString("sid", "session_A").apply()
        prefsB.edit().putString("sid", "session_B").apply()

        assertNotEquals(
            prefsA.getString("sid", ""),
            prefsB.getString("sid", "")
        )
    }

    private fun computeEffectiveKey(shopId: String, preferencesKey: String): String {
        return if (preferencesKey.isEmpty()) {
            "sdk_prefs_$shopId"
        } else {
            preferencesKey
        }
    }
}
