package com.personalization

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.messaging.RemoteMessage
import com.personalization.sdk.data.models.dto.notification.NotificationData
import io.mockk.every
import io.mockk.mockk
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
 * Covers both push entry points routing identically through [PushTargetResolver]: [Rees46.handlePush]
 * (a host that owns its messaging service) and the companion [SDK.onMessage] (the SDK's own
 * MessagingService / HmsMessagingService). The payload's shop_id names the target; a single-shop app
 * resolves with no shop_id; an unknown shop_id — or none while several shops are live — is dropped
 * rather than delivered against the wrong shop.
 *
 * Bare `SDK()` instances are registered directly — receiveMessage skips its network call while
 * uninitialized but still forwards to the message listener, so routing is observable without a real
 * init. The CLICKED dispatch needs an initialized instance and is only exercised here for the drop
 * cases, which return before dispatch.
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

    @Suppress("DEPRECATION") // exercises the legacy per-instance listener, which must keep working
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

    // --- SDK.onMessage (the SDK's own messaging services) routes the same way ---

    @Test
    fun `onMessage delivers to the instance named by its shop_id`() {
        val a = SDK()
        val b = SDK()
        SdkRegistry.register("shop-a", a)
        SdkRegistry.register("shop-b", b)
        val toA = mutableListOf<NotificationData>()
        val toB = mutableListOf<NotificationData>()
        a.captureMessages(toA)
        b.captureMessages(toB)

        SDK.onMessage(mapOf("shop_id" to "shop-a", "type" to "bulk"))

        assertEquals(1, toA.size)
        assertTrue(toB.isEmpty())
    }

    @Test
    fun `onMessage with no shop_id delivers to the single live instance`() {
        val only = SDK()
        SdkRegistry.register("shop-a", only)
        val received = mutableListOf<NotificationData>()
        only.captureMessages(received)

        SDK.onMessage(mapOf("type" to "bulk"))

        assertEquals(1, received.size)
    }

    @Test
    fun `onMessage for an unknown shop is dropped`() {
        val a = SDK()
        SdkRegistry.register("shop-a", a)
        val received = mutableListOf<NotificationData>()
        a.captureMessages(received)

        SDK.onMessage(mapOf("shop_id" to "shop-x", "type" to "bulk"))

        assertTrue(received.isEmpty())
    }

    @Test
    fun `onMessage with no shop_id is dropped when several shops are live`() {
        val a = SDK()
        val b = SDK()
        SdkRegistry.register("shop-a", a)
        SdkRegistry.register("shop-b", b)
        val toA = mutableListOf<NotificationData>()
        val toB = mutableListOf<NotificationData>()
        a.captureMessages(toA)
        b.captureMessages(toB)

        SDK.onMessage(mapOf("type" to "bulk"))

        assertTrue(toA.isEmpty())
        assertTrue(toB.isEmpty())
    }

    // --- Rees46.setOnMessageListener (R1): one process-global listener covers every shop ---

    @Test
    fun `the global listener receives every shop's push with the routed shopId`() {
        SdkRegistry.register("shop-a", SDK())
        SdkRegistry.register("shop-b", SDK())
        val received = mutableListOf<Pair<String, NotificationData>>()
        Rees46.setOnMessageListener { shopId, data -> received.add(shopId to data) }

        // A single registration — no per-instance wiring — sees both shops, each with its own id.
        Rees46.handlePush(mapOf("shop_id" to "shop-a", "type" to "bulk"), PushEventType.RECEIVED)
        SDK.onMessage(mapOf("shop_id" to "shop-b", "type" to "bulk"))

        assertEquals(2, received.size)
        assertEquals("shop-a", received[0].first)
        assertEquals("shop-b", received[1].first)
    }

    @Test
    fun `the global listener does not fire for a dropped push`() {
        SdkRegistry.register("shop-a", SDK())
        SdkRegistry.register("shop-b", SDK())
        val received = mutableListOf<String>()
        Rees46.setOnMessageListener { shopId, _ -> received.add(shopId) }

        SDK.onMessage(mapOf("shop_id" to "shop-x", "type" to "bulk")) // unknown shop
        SDK.onMessage(mapOf("type" to "bulk")) // no shop_id, several live → ambiguous

        assertTrue(received.isEmpty())
    }

    @Test
    fun `a no-shop_id push with a single live shop still reaches the global listener`() {
        SdkRegistry.register("shop-a", SDK())
        val received = mutableListOf<String>()
        Rees46.setOnMessageListener { shopId, _ -> received.add(shopId) }

        SDK.onMessage(mapOf("type" to "bulk"))

        assertEquals(listOf("shop-a"), received)
    }

    // --- The SDK's own FCM messaging service routes through Rees46.handlePush (the pending-aware router).
    // Materializing a lazily-registered shop needs a real init (network/resources), so the pending path
    // is exercised on-device in the demo's instrumented MultiInstanceE2ETest; here we pin the wiring: a
    // data push from the FCM service reaches the shop its shop_id names. ---

    @Test
    fun `the FCM messaging service routes a data push to its shop`() {
        SdkRegistry.register("shop-b", SDK())
        val received = mutableListOf<Pair<String, NotificationData>>()
        Rees46.setOnMessageListener { shopId, data -> received.add(shopId to data) }

        val message = mockk<RemoteMessage>(relaxed = true)
        every { message.data } returns mapOf("shop_id" to "shop-b", "type" to "bulk")
        every { message.notification } returns null

        MessagingService().onMessageReceived(message)

        assertEquals(1, received.size)
        assertEquals("shop-b", received.first().first)
    }

    @Test
    fun `a received push shows a pending shop via the global listener without initializing it first`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Rees46.registerShops(
            context = context,
            configs = listOf(Rees46Config(shopId = "shop-b", autoSendPushToken = false)),
            eagerInit = false
        )
        val received = mutableListOf<Pair<String, NotificationData>>()
        Rees46.setOnMessageListener { shopId, data -> received.add(shopId to data) }

        // Display happens before (and independently of) the track-time bring-up. The light init that
        // would track `received` can't run under Robolectric (real notification resources), but that
        // failure is swallowed and must not stop the notification from being shown — exactly the
        // guarantee for a cold-process push to a not-yet-initialized shop.
        Rees46.handlePush(mapOf("shop_id" to "shop-b", "type" to "bulk"), PushEventType.RECEIVED)

        assertEquals(1, received.size)
        assertEquals("shop-b", received.first().first)
    }
}
