package com.personalization.core.internal

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.personalization.RegisterManager
import com.personalization.sdk.data.repositories.preferences.PreferencesDataSourceImpl
import com.personalization.sdk.data.repositories.userSettings.UserSettingsRepositoryImpl
import com.personalization.sdk.domain.usecases.network.SendNetworkMethodUseCase
import com.personalization.sdk.domain.usecases.userSettings.GetUserSettingsValueUseCase
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.TimeZone

/**
 * The `tz` of `/init` in whole hours (`core/init.md`), for the zones half an hour off: India's +5:30 goes
 * as `5`, Newfoundland's −3:30 as `-3`, on both cores — the new core from the offset of now, summer time
 * included, the legacy one from the zone's raw offset, as 2.49.0 sent it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class InitTimeZoneTest {

    private lateinit var context: Context
    private val layout = StorageLayout(SHOP, null)
    private val defaultZone = TimeZone.getDefault()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(layout.partitionName, Context.MODE_PRIVATE).edit().clear().commit()
        File(context.noBackupFilesDir, ShopStorage.ROOT_DIRECTORY).deleteRecursively()
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(defaultZone)
    }

    /** The `tz` the new core's first `/init` carries in [zone] at [now]. */
    private fun coreTz(zone: String, now: Long): String {
        val scheduler = TestCoroutineScheduler()
        val dispatchers = testCoreDispatchers(scheduler)
        val settings = UserSettingsRepositoryImpl(PreferencesDataSourceImpl().apply { initialize(context, layout.partitionName, shopId = SHOP) })
        val storage = ShopStorage(context, layout)
        val sent = mutableListOf<JSONObject>()
        val init = CoreInit(
            setup = InitSetup(SHOP),
            send = { params ->
                sent += params
                HttpResult.Success(JSONObject().put("did", "D").put("seance", "S"))
            },
            settings = settings,
            storage = storage,
            state = InstanceState(shopId = { SHOP }, dispatchers = dispatchers),
            connectivity = FakeConnectivity(connected = true),
            lifecycle = FakeAppLifecycle(foreground = true),
            hooks = object : InitHooks {
                override fun onAnswer(answer: JSONObject, fresh: Boolean) = Unit
                override fun onUsable() = Unit
            },
            dispatchers = dispatchers,
            clock = FakeClock(now),
            timeZone = { TimeZone.getTimeZone(zone) },
        )
        init.start()
        scheduler.runCurrent()
        init.release()
        return sent.single().getString("tz")
    }

    /** The `tz` the legacy core's `/init` carries with [zone] as the device's. */
    private fun legacyTz(zone: String): String {
        TimeZone.setDefault(TimeZone.getTimeZone(zone))
        val network = mockk<SendNetworkMethodUseCase>(relaxed = true)
        val get = mockk<GetUserSettingsValueUseCase>(relaxed = true)
        every { get.getDid() } returns ""
        val params = slot<JSONObject>()
        every { network.get(eq("init"), capture(params), any()) } returns Unit
        RegisterManager(mockk(relaxed = true), get, network, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true))
            .initialize(context, context.contentResolver, autoSendPushToken = false)
        return params.captured.getString("tz")
    }

    @Test
    fun `a zone half an hour off goes in whole hours toward zero, on both cores`() {
        assertEquals("5", coreTz("Asia/Kolkata", JANUARY))
        assertEquals("-3", coreTz("America/St_Johns", JANUARY))
        assertEquals("5", legacyTz("Asia/Kolkata"))
        assertEquals("-3", legacyTz("America/St_Johns"))
    }

    @Test
    fun `the new core sends the offset of now, the legacy one the zone's raw offset, in summer time`() {
        // Newfoundland in July is −2:30.
        assertEquals("-2", coreTz("America/St_Johns", JULY))
        assertEquals("-3", legacyTz("America/St_Johns"))
        assertEquals("3", coreTz("Europe/Moscow", JULY))
        assertEquals("0", coreTz("UTC", JULY))
    }

    private companion object {
        const val SHOP = "tz-shop"

        /** 2026-01-15 and 2026-07-15, noon UTC. */
        const val JANUARY = 1_768_478_400_000L
        const val JULY = 1_784_116_800_000L
    }
}
