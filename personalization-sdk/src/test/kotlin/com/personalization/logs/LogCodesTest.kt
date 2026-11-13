package com.personalization.logs

import androidx.test.core.app.ApplicationProvider
import com.personalization.LogLevel
import com.personalization.Rees46
import com.personalization.RegisterManager
import com.personalization.SdkLogMessage
import com.personalization.api.OnApiCallbackListener
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Modifier
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor

/** The SDK writes only the codes of the logging spec's table, each at its level there. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LogCodesTest {

    private val received = CopyOnWriteArrayList<SdkLogMessage>()
    private lateinit var previousExecutor: Executor

    @Before
    fun setUp() {
        SdkLogs.reset()
        previousExecutor = SdkLogs.handlerExecutor
        SdkLogs.handlerExecutor = Executor { it.run() }
        Rees46.setLogHandler { received += it }
        SdkLogs.register("shop", LogLevel.DEBUG, "SDK")
    }

    @After
    fun tearDown() {
        SdkLogs.reset()
        SdkLogs.handlerExecutor = previousExecutor
    }

    @Test
    fun `every code is in the table of the spec`() {
        val codes = LogCodes::class.java.declaredFields
            .filter { Modifier.isStatic(it.modifiers) && it.type == String::class.java }
            .map { it.get(null) as String }
        assertTrue(codes.isNotEmpty())
        assertEquals(emptyList<String>(), codes - SPEC_CODES)
    }

    @Test
    fun `a failure is an error on a 4xx and a warning otherwise`() {
        assertEquals(LogLevel.ERROR, LogCodes.failureLevel(403))
        assertEquals(LogLevel.WARNING, LogCodes.failureLevel(503))
        assertEquals(LogLevel.WARNING, LogCodes.failureLevel(-1))
        assertEquals(LogLevel.WARNING, LogCodes.failureLevel(null))
    }

    @Test
    fun `a 4xx to init is a failed init, not a refused shop`() {
        val init = startInit()

        init.onError(403, """{"message":"blocked"}""")

        assertEquals(listOf(LogCodes.INIT_FAILED to LogLevel.ERROR), received.map { it.code to it.level })
        assertEquals(403, received.single().context["status"])
    }

    @Test
    fun `an answered init is logged as succeeded`() {
        val init = startInit()

        init.onSuccess(JSONObject("""{"did":"D","seance":"S"}"""))

        assertEquals(listOf(LogCodes.INIT_SUCCEEDED to LogLevel.INFO), received.map { it.code to it.level })
    }

    private fun startInit(): OnApiCallbackListener {
        val network = mockk<com.personalization.sdk.domain.usecases.network.SendNetworkMethodUseCase>(relaxed = true)
        val settings = mockk<com.personalization.sdk.domain.usecases.userSettings.GetUserSettingsValueUseCase>(relaxed = true)
        every { settings.getDid() } returns ""
        every { settings.getShopId() } returns "shop"
        val listener = slot<OnApiCallbackListener>()
        every { network.get(eq("init"), any(), capture(listener)) } just Runs
        val manager = RegisterManager(mockk(relaxed = true), settings, network, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true))
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        manager.initialize(context, context.contentResolver, autoSendPushToken = false)
        received.clear()
        return listener.captured
    }

    private companion object {
        /** The table "Коды" of the logging spec, with the area codes of the older code. */
        val SPEC_CODES = setOf(
            "request", "request_failed", "request_build_failed", "invalid_argument", "invalid_config",
            "decoding_failed", "callback_failed", "init_succeeded", "init_saved_answer", "init_retry",
            "init_failed", "init_refused", "device_id_reset", "storage_failed", "queue_dropped",
            "push_received", "push_dropped", "push_failed", "push_permission", "push_permission_failed",
            "push_token_sent", "push_token_skipped", "push_token_failed", "advertising_id_sent",
            "advertising_id_failed", "popup_not_shown", "core_flag_mismatch", "not_implemented",
            "sdk_log", "ui", "stories", "stories_v3", "popups_v3",
        )
    }
}
