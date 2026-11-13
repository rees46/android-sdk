package com.personalization.logs

import com.personalization.LogLevel
import com.personalization.Rees46
import com.personalization.SDK
import com.personalization.SdkLogMessage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor

/** The SDK's log: levels per shop, the app's handler, and what reaches logcat. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SdkLogsTest {

    private val received = CopyOnWriteArrayList<SdkLogMessage>()
    private lateinit var previousExecutor: Executor

    @Before
    fun setUp() {
        SdkLogs.reset()
        previousExecutor = SdkLogs.handlerExecutor
        SdkLogs.handlerExecutor = Executor { it.run() }
        Rees46.setLogHandler { received += it }
        ShadowLog.clear()
    }

    @After
    fun tearDown() {
        SdkLogs.reset()
        SdkLogs.handlerExecutor = previousExecutor
    }

    @Test
    fun `warning is the default level`() {
        SdkLogs.log(LogLevel.DEBUG, "c_debug", "debug")
        SdkLogs.log(LogLevel.INFO, "c_info", "info")
        SdkLogs.log(LogLevel.WARNING, "c_warning", "warning")
        SdkLogs.log(LogLevel.ERROR, "c_error", "error")

        assertEquals(listOf("c_warning", "c_error"), received.map { it.code })
    }

    @Test
    fun `a shop's message passes that shop's level`() {
        SdkLogs.register("debug-shop", LogLevel.DEBUG, "SDK")
        SdkLogs.register("quiet-shop", LogLevel.ERROR, "SDK")

        SdkLogs.log(LogLevel.DEBUG, "request", "to debug-shop", shopId = "debug-shop")
        SdkLogs.log(LogLevel.WARNING, "init_retry", "to quiet-shop", shopId = "quiet-shop")
        SdkLogs.log(LogLevel.ERROR, "init_refused", "to quiet-shop", shopId = "quiet-shop")

        assertEquals(listOf("request" to "debug-shop", "init_refused" to "quiet-shop"), received.map { it.code to it.shopId })
    }

    @Test
    fun `a message without a shop passes the most detailed level of any shop`() {
        SdkLogs.register("a", LogLevel.ERROR, "SDK")
        SdkLogs.register("b", LogLevel.INFO, "SDK")

        SdkLogs.log(LogLevel.DEBUG, "c_debug", "debug")
        SdkLogs.log(LogLevel.INFO, "c_info", "info")

        assertEquals(listOf("c_info"), received.map { it.code })
    }

    @Test
    fun `none turns the shop's log off, logcat included`() {
        SdkLogs.register("silent", LogLevel.NONE, "SilentTag")

        SdkLogs.log(LogLevel.ERROR, "init_refused", "refused", shopId = "silent")

        assertTrue(received.isEmpty())
        assertTrue(ShadowLog.getLogsForTag("SilentTag").isEmpty())
    }

    @Test
    fun `the handler gets the code, the shop and the context`() {
        SdkLogs.register("shop", LogLevel.WARNING, "SDK")

        SdkLogs.log(
            LogLevel.ERROR,
            "request_failed",
            "Request failed",
            shopId = "shop",
            context = mapOf("path" to "push", "status" to 500),
            throwable = IllegalStateException("boom"),
        )

        val message = received.single()
        assertEquals(LogLevel.ERROR, message.level)
        assertEquals("Request failed", message.message)
        assertEquals("shop", message.shopId)
        assertEquals(mapOf("path" to "push", "status" to 500, "exception" to "IllegalStateException"), message.context)
    }

    @Test
    fun `a handler that throws does not reach the caller`() {
        Rees46.setLogHandler { throw IllegalStateException("the app's handler failed") }

        SdkLogs.log(LogLevel.ERROR, "init_refused", "refused")

        assertTrue(ShadowLog.getLogs().any { it.msg.contains("log_handler_failed") })
    }

    @Test
    fun `logcat gets the code and the shop's tag`() {
        SdkLogs.register("shop", LogLevel.DEBUG, "ShopTag")

        SdkLogs.log(LogLevel.INFO, "push_token_sent", "Push token sent", shopId = "shop", context = mapOf("provider" to "fcm"))

        val line = ShadowLog.getLogsForTag("ShopTag").single().msg
        assertEquals("[push_token_sent] Push token sent {provider=fcm}", line)
    }

    @Test
    fun `the static SDK functions write through the log at their level`() {
        SDK.debug("hidden at the default level")
        SDK.warn("shown")

        assertEquals(listOf("sdk_log" to "shown"), received.map { it.code to it.message })
        assertFalse(received.any { it.message.contains("hidden") })
    }

    @Test
    fun `log levels allow from their own level up`() {
        assertTrue(LogLevel.WARNING.allows(LogLevel.ERROR))
        assertTrue(LogLevel.WARNING.allows(LogLevel.WARNING))
        assertFalse(LogLevel.WARNING.allows(LogLevel.INFO))
        assertFalse(LogLevel.NONE.allows(LogLevel.ERROR))
        assertFalse(LogLevel.DEBUG.allows(LogLevel.NONE))
    }
}
