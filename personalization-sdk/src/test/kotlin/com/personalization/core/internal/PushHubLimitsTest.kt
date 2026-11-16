package com.personalization.core.internal

import com.personalization.LogLevel
import com.personalization.PushProvider
import com.personalization.SdkLogHandler
import com.personalization.SdkLogMessage
import com.personalization.core.BridgeApi
import com.personalization.core.PushCallbacks
import com.personalization.core.PushEvent
import com.personalization.core.PushPayload
import com.personalization.core.PushToken
import com.personalization.logs.LogCodes
import com.personalization.logs.SdkLogs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

/**
 * What [PushHub] keeps is bounded, and nothing it runs for the app stops it: the messages whose events
 * went and the pushes that came are forgotten oldest first, a parked repeat that throws is logged and
 * the others still try, a main thread that refuses a callback is logged, and a bridge that joins while a
 * token comes hears it exactly once.
 */
@OptIn(BridgeApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PushHubLimitsTest {

    private val logged = CopyOnWriteArrayList<SdkLogMessage>()
    private val defaultExecutor = SdkLogs.handlerExecutor

    @Before
    fun setUp() {
        PushHub.reset()
        PushHub.main = Executor { it.run() }
        SdkLogs.reset()
        SdkLogs.handlerExecutor = Executor { it.run() }
        SdkLogs.register(SHOP, LogLevel.DEBUG, "SDK")
        SdkLogs.handler = SdkLogHandler { logged += it }
    }

    @After
    fun tearDown() {
        PushHub.reset()
        SdkLogs.reset()
        SdkLogs.handlerExecutor = defaultExecutor
    }

    private fun payload(code: String) =
        PushPayload.parse(mapOf("id" to code, "type" to "bulk"))?.withShop(SHOP) ?: throw AssertionError("not a push")

    @Test
    fun `past a thousand other messages the oldest message's event may go again`() {
        fun sent(code: String) {
            assertEquals(PushHub.Reservation.TAKEN, PushHub.reserve(SHOP, PushEvent.DELIVERED, code) {})
            PushHub.confirm(SHOP, PushEvent.DELIVERED, code)
        }
        sent("oldest")
        // 999 more: the oldest is still remembered.
        repeat(SEEN_LIMIT - 1) { sent("m-$it") }
        assertEquals(PushHub.Reservation.REPEAT, PushHub.reserve(SHOP, PushEvent.DELIVERED, "oldest") {})

        sent("one-more")

        assertEquals(PushHub.Reservation.TAKEN, PushHub.reserve(SHOP, PushEvent.DELIVERED, "oldest") {})
        assertEquals("a recent one is still a repeat", PushHub.Reservation.REPEAT, PushHub.reserve(SHOP, PushEvent.DELIVERED, "one-more") {})
    }

    @Test
    fun `past a hundred pushes the oldest one is forgotten`() {
        PushHub.remember(payload("oldest"))
        repeat(PUSHES_LIMIT - 1) { PushHub.remember(payload("p-$it")) }
        assertNotNull(PushHub.received("oldest", "bulk"))

        PushHub.remember(payload("newest"))

        assertNull(PushHub.received("oldest", "bulk"))
        assertEquals(SHOP, PushHub.received("newest", "bulk")?.get("shop_id"))
    }

    @Test
    fun `a parked repeat that throws is logged and the others still try`() {
        val tried = CopyOnWriteArrayList<String>()
        PushHub.reserve(SHOP, PushEvent.CLICKED, "m") {}
        PushHub.reserve(SHOP, PushEvent.CLICKED, "m") { throw IllegalStateException("the repeat failed") }
        PushHub.reserve(SHOP, PushEvent.CLICKED, "m") { tried += "third" }

        PushHub.release(SHOP, PushEvent.CLICKED, "m")

        assertEquals(listOf("third"), tried)
        val failed = logged.single { it.code == LogCodes.PUSH_FAILED }
        assertEquals(LogLevel.ERROR, failed.level)
        assertEquals(SHOP, failed.shopId)
    }

    @Test
    fun `a main thread that refuses a callback is logged, never thrown at the push's handling`() {
        val heard = CopyOnWriteArrayList<String>()
        PushHub.callbacks = PushCallbacks(onClicked = { heard += it.code }, onToken = { heard += it.token })
        PushHub.main = Executor { throw RejectedExecutionException("the main thread is gone") }

        PushHub.event(PushEvent.CLICKED, payload("m"), own = null)
        PushHub.token(PushToken("t", PushProvider.FCM), SHOP, own = null, ownReported = ConcurrentHashMap())

        assertTrue(heard.isEmpty())
        assertEquals(2, logged.count { it.code == LogCodes.CALLBACK_FAILED && it.level == LogLevel.ERROR })
    }

    @Test
    fun `a bridge that joins while a token comes hears it exactly once`() {
        repeat(RACE_ROUNDS) { round ->
            PushHub.reset()
            PushHub.main = Executor { it.run() }
            val heard = CopyOnWriteArrayList<String>()
            val barrier = CyclicBarrier(2)
            val joining = Thread {
                barrier.await()
                PushHub.addTokenSink { heard += it.token }
            }
            val coming = Thread {
                barrier.await()
                PushHub.token(PushToken("t", PushProvider.FCM), SHOP, own = null, ownReported = ConcurrentHashMap())
            }
            joining.start()
            coming.start()
            joining.join(5_000)
            coming.join(5_000)

            assertEquals("round $round", listOf("t"), heard)
        }
    }

    private companion object {
        const val SHOP = "hub-limits-shop"

        /** PushHub's own bounds: the events of this many messages, the last this many pushes. */
        const val SEEN_LIMIT = 1_000
        const val PUSHES_LIMIT = 100

        const val RACE_ROUNDS = 1_000
    }
}
