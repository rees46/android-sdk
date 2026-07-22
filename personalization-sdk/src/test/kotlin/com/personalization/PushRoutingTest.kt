package com.personalization

import com.personalization.sdk.data.models.dto.notification.NotificationData
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers [Rees46.handlePush] delivering a RECEIVED push to the instance named by the payload's
 * shop_id (Release 4). Bare `SDK()` instances are registered directly — receiveMessage skips its
 * network call while uninitialized but still forwards to the message listener, so routing is
 * observable without a real init. The CLICKED dispatch needs an initialized instance and is only
 * exercised here for the drop cases, which return before dispatch.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PushRoutingTest {

    @Before
    fun setUp() {
        SdkRegistry.reset()
        Rees46.reset()
    }

    @After
    fun tearDown() {
        SdkRegistry.reset()
        Rees46.reset()
    }

    private fun SDK.captureMessages(into: MutableList<NotificationData>) {
        setOnMessageListener { data -> into.add(data) }
    }

    @Test
    fun `a received push is delivered to the instance named by its shop_id`() {
        val a = SDK()
        val b = SDK()
        SdkRegistry.register("shop-a", a)
        SdkRegistry.register("shop-b", b)
        val toA = mutableListOf<NotificationData>()
        val toB = mutableListOf<NotificationData>()
        a.captureMessages(toA)
        b.captureMessages(toB)

        Rees46.handlePush(mapOf("shop_id" to "shop-a", "type" to "bulk"), PushEventType.RECEIVED)

        assertEquals(1, toA.size)
        assertTrue(toB.isEmpty())
    }

    @Test
    fun `with no shop_id a single instance still receives the push`() {
        val only = SDK()
        SdkRegistry.register("shop-a", only)
        val received = mutableListOf<NotificationData>()
        only.captureMessages(received)

        Rees46.handlePush(mapOf("type" to "bulk"), PushEventType.RECEIVED)

        assertEquals(1, received.size)
    }

    @Test
    fun `a push for an unknown shop is dropped`() {
        val a = SDK()
        SdkRegistry.register("shop-a", a)
        val received = mutableListOf<NotificationData>()
        a.captureMessages(received)

        Rees46.handlePush(mapOf("shop_id" to "shop-x", "type" to "bulk"), PushEventType.RECEIVED)

        assertTrue(received.isEmpty())
    }

    @Test
    fun `a push with no shop_id is dropped when several shops are live`() {
        val a = SDK()
        val b = SDK()
        SdkRegistry.register("shop-a", a)
        SdkRegistry.register("shop-b", b)
        val toA = mutableListOf<NotificationData>()
        val toB = mutableListOf<NotificationData>()
        a.captureMessages(toA)
        b.captureMessages(toB)

        Rees46.handlePush(mapOf("type" to "bulk"), PushEventType.RECEIVED)

        assertTrue(toA.isEmpty())
        assertTrue(toB.isEmpty())
    }

    @Test
    fun `a clicked push for an unknown shop is dropped without dispatch`() {
        SdkRegistry.register("shop-a", SDK())

        // Resolves to null and returns before touching the click path (which needs an init'd SDK).
        Rees46.handlePush(mapOf("shop_id" to "shop-x"), PushEventType.CLICKED)

        assertFalse(Rees46.isInitialized("shop-x"))
    }
}
