package com.personalization.core.internal

import android.os.Looper
import com.personalization.LogLevel
import com.personalization.SdkLogHandler
import com.personalization.SdkLogMessage
import com.personalization.core.SdkState
import com.personalization.logs.LogCodes
import com.personalization.logs.SdkLogs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor

/** The state's listeners: the current state at once, then on the main thread once per change, in order, a throwing one logged. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class InstanceStateTest {

    private val logged = CopyOnWriteArrayList<SdkLogMessage>()
    private val defaultExecutor = SdkLogs.handlerExecutor

    /** On the real main dispatcher: the main looper delivers. */
    private val state = InstanceState(shopId = { "shop" })

    @Before
    fun setUp() {
        SdkLogs.reset()
        SdkLogs.handlerExecutor = Executor { it.run() }
        SdkLogs.register("shop", LogLevel.DEBUG, "SDK")
        SdkLogs.handler = SdkLogHandler { logged += it }
    }

    @After
    fun tearDown() {
        SdkLogs.reset()
        SdkLogs.handlerExecutor = defaultExecutor
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun `each change reaches a listener once, on the main thread, in order`() {
        val heard = mutableListOf<Pair<SdkState, Boolean>>()
        state.addListener { heard += it to (Looper.myLooper() == Looper.getMainLooper()) }

        // From a background thread, as the cores move it.
        val done = CountDownLatch(1)
        Thread {
            state.set(SdkState.INITIALIZING)
            state.set(SdkState.INITIALIZING)
            state.set(SdkState.RETRYING)
            state.set(SdkState.READY)
            done.countDown()
        }.start()
        done.await()
        idle()

        assertEquals(
            listOf(SdkState.NOT_INITIALIZED to true, SdkState.INITIALIZING to true, SdkState.RETRYING to true, SdkState.READY to true),
            heard,
        )
        assertEquals(SdkState.READY, state.current)
    }

    @Test
    fun `a new listener hears the current state at once on the main thread, then the changes`() {
        state.set(SdkState.RETRYING)
        idle()
        val heard = mutableListOf<Pair<SdkState, Boolean>>()

        state.addListener { heard += it to (Looper.myLooper() == Looper.getMainLooper()) }
        assertEquals("delivered on the main thread, not inside addListener", emptyList<Pair<SdkState, Boolean>>(), heard)
        idle()
        assertEquals(listOf(SdkState.RETRYING to true), heard)

        state.set(SdkState.READY)
        idle()
        assertEquals(listOf(SdkState.RETRYING to true, SdkState.READY to true), heard)
    }

    @Test
    fun `a change made right after adding a listener comes after the current state, never before`() {
        val heard = CopyOnWriteArrayList<SdkState>()
        val done = CountDownLatch(1)
        Thread {
            state.addListener { heard += it }
            state.set(SdkState.INITIALIZING)
            state.set(SdkState.READY)
            done.countDown()
        }.start()
        done.await()
        idle()

        assertEquals(listOf(SdkState.NOT_INITIALIZED, SdkState.INITIALIZING, SdkState.READY), heard)
    }

    @Test
    fun `the same state again is no change`() {
        val heard = mutableListOf<SdkState>()
        state.addListener { heard += it }

        assertTrue(state.set(SdkState.READY))
        assertFalse(state.set(SdkState.READY))
        idle()

        assertEquals(listOf(SdkState.NOT_INITIALIZED, SdkState.READY), heard)
    }

    @Test
    fun `a listener that throws is logged and keeps hearing, the others are not disturbed`() {
        val others = mutableListOf<SdkState>()
        var calls = 0
        state.addListener {
            calls++
            throw IllegalStateException("app")
        }
        state.addListener { others += it }

        state.set(SdkState.INITIALIZING)
        state.set(SdkState.READY)
        idle()

        assertEquals(3, calls)
        assertEquals(listOf(SdkState.NOT_INITIALIZED, SdkState.INITIALIZING, SdkState.READY), others)
        val failures = logged.filter { it.code == LogCodes.CALLBACK_FAILED }
        assertEquals(3, failures.size)
        assertTrue(failures.all { it.level == LogLevel.ERROR && it.shopId == "shop" })
    }

    @Test
    fun `a cancelled listener hears nothing more, even a change already on its way`() {
        val heard = mutableListOf<SdkState>()
        val handle = state.addListener { heard += it }

        state.set(SdkState.INITIALIZING)
        // Cancelled before the main thread ran: not even the current state arrives.
        handle.cancel()
        state.set(SdkState.READY)
        idle()

        assertEquals(emptyList<SdkState>(), heard)
    }

    @Test
    fun `dropped listeners still hear the change made before`() {
        val heard = mutableListOf<SdkState>()
        state.addListener { heard += it }

        state.set(SdkState.READY)
        state.set(SdkState.NOT_INITIALIZED)
        state.dropListeners()
        state.set(SdkState.INITIALIZING)
        idle()

        assertEquals(listOf(SdkState.NOT_INITIALIZED, SdkState.READY, SdkState.NOT_INITIALIZED), heard)
    }
}
