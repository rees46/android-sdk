package com.personalization

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.personalization.sdk.data.repositories.preferences.PreferencesDataSourceImpl
import com.personalization.sdk.data.repositories.userSettings.UserSettingsRepositoryImpl
import com.personalization.sdk.domain.usecases.network.SendNetworkMethodUseCase
import com.personalization.sdk.domain.usecases.userSettings.GetUserSettingsValueUseCase
import com.personalization.sdk.domain.usecases.userSettings.UpdateUserSettingsValueUseCase
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The single-shop upgrade guarantee, asserted through the composed layers the SDK actually uses —
 * not just the [PreferencesDataSourceImpl] migration in isolation (that is [PreferencesDataSourceMigrationTest])
 * nor [RegisterManager] over a *mocked* did (that is [RegisterManagerDidTest]).
 *
 * A user who was on the pre-partition SDK stored did/sid in the legacy shared file
 * ([PreferencesPartition.LEGACY_KEY]). After updating, the SDK reads identity from a per-shop
 * partition. These tests wire the real chain — legacy prefs → migration → [UserSettingsRepositoryImpl]
 * → [GetUserSettingsValueUseCase] (exactly what `SDK.getDid()` / `getSid()` resolve through) → the real
 * [RegisterManager] — over real Robolectric SharedPreferences, mocking only the network/queue/push
 * collaborators. This closes the "assumption not asserted through the composed SDK" gap: that the
 * migrated did both survives to the getter the SDK exposes and short-circuits re-registration.
 *
 * Full `SDK.initialize` is intentionally not driven here: it builds the whole Dagger graph and touches
 * notification/FCM/GAID/network, which is why it is covered by the instrumented demo E2E, not a JVM
 * unit test. This asserts the same identity-preservation contract at the layer below, deterministically
 * and with no backend.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SdkUpgradeMigrationTest {

    private lateinit var context: Context

    private val legacyKey = PreferencesPartition.LEGACY_KEY
    private val shopId = "shop-a"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Robolectric keeps SharedPreferences between tests in the same process — clear both files.
        context.getSharedPreferences(legacyKey, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences(PreferencesPartition.keyFor(shopId), Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    private fun seedLegacy(vararg entries: Pair<String, String>) {
        val editor = context.getSharedPreferences(legacyKey, Context.MODE_PRIVATE).edit()
        entries.forEach { (k, v) -> editor.putString(k, v) }
        editor.commit()
    }

    /** The real migration into [shop]'s partition, the exact call `SDK.initialize` makes for the default. */
    private fun migratedPartition(shop: String): PreferencesDataSourceImpl =
        PreferencesDataSourceImpl().apply {
            initialize(
                context = context,
                preferencesKey = PreferencesPartition.keyFor(shop),
                legacyPreferencesKey = legacyKey,
                shopId = shop
            )
        }

    @Test
    fun `a single-shop upgrade keeps did and sid at the settings layer the SDK reads`() {
        seedLegacy(
            PreferencesPartition.SHOP_ID_FIELD to shopId,
            "did" to "device-123",
            "sid" to "seance-456"
        )

        // GetUserSettingsValueUseCase is exactly what SDK.getDid()/getSid() delegate to.
        val get = GetUserSettingsValueUseCase(UserSettingsRepositoryImpl(migratedPartition(shopId)))

        assertEquals("device-123", get.getDid())
        assertEquals("seance-456", get.getSid())
    }

    @Test
    fun `an upgraded install reading the migrated did is not re-registered`() {
        seedLegacy(
            PreferencesPartition.SHOP_ID_FIELD to shopId,
            "did" to "device-123",
            "sid" to "seance-456"
        )

        val repository = UserSettingsRepositoryImpl(migratedPartition(shopId))
        val get = GetUserSettingsValueUseCase(repository)
        val update = UpdateUserSettingsValueUseCase(repository)
        val network = mockk<SendNetworkMethodUseCase>(relaxed = true)
        val manager = RegisterManager(
            update,
            get,
            network,
            mockk(relaxed = true), // ExecuteQueueTasksUseCase
            mockk(relaxed = true), // InAppNotificationManager
            mockk(relaxed = true)  // PushTokenManager
        )

        manager.initialize(context, context.contentResolver, autoSendPushToken = false)

        // The migrated did is non-empty, so RegisterManager takes the existing-install branch: no /init
        // round-trip fires and the did is left as-is — nobody is silently re-identified on upgrade.
        verify(exactly = 0) { network.get(eq("init"), any(), any()) }
        assertEquals("device-123", get.getDid())
    }
}
