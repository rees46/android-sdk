package com.personalization.core.internal

import com.personalization.LogLevel
import com.personalization.Rees46
import com.personalization.SdkLogMessage
import com.personalization.logs.SdkLogs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor

/** One core for the process: the first instance decides, the others follow and are told. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CoreSelectionTest {

    private val received = CopyOnWriteArrayList<SdkLogMessage>()
    private lateinit var previousExecutor: Executor

    @Before
    fun setUp() {
        CoreSelection.reset()
        SdkLogs.reset()
        previousExecutor = SdkLogs.handlerExecutor
        SdkLogs.handlerExecutor = Executor { it.run() }
        Rees46.setLogHandler { received += it }
    }

    @After
    fun tearDown() {
        CoreSelection.reset()
        SdkLogs.reset()
        SdkLogs.handlerExecutor = previousExecutor
    }

    @Test
    fun `the first instance decides the core of the process`() {
        assertTrue(CoreSelection.resolve(requested = true, shopId = "a"))
        assertTrue(CoreSelection.resolve(requested = true, shopId = "b"))
        assertTrue(received.isEmpty())
    }

    @Test
    fun `an instance that asks for the other core gets the process's and a warning`() {
        assertFalse(CoreSelection.resolve(requested = false, shopId = "a"))

        assertFalse(CoreSelection.resolve(requested = true, shopId = "b"))

        val warning = received.single()
        assertEquals("core_flag_mismatch" to LogLevel.WARNING, warning.code to warning.level)
        assertEquals("b", warning.shopId)
        assertEquals(mapOf("requested" to true, "process" to false), warning.context)
    }
}
