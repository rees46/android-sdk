package com.personalization

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers the readiness notifier (Release 3): [SdkRegistry.onNextRegister] and the public
 * [Rees46.awaitInstance] / [Rees46.isInitialized]. Lets a UI element resolve its SDK when the
 * instance becomes available instead of the host wiring it in — event-driven, no polling.
 *
 * Bare `SDK()` instances are registered straight into [SdkRegistry]; the notifier only stores and
 * hands back references, so no real initialization (or network) is needed. The lazy-materialization
 * branch of awaitInstance (pending -> initialize) would hit the network and is left out here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SdkNotifierTest {

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

    // --- SdkRegistry.onNextRegister ---

    @Test
    fun `onNextRegister fires when the awaited shop registers`() {
        var received: SDK? = null
        SdkRegistry.onNextRegister("shop-a") { received = it }
        assertNull(received)

        val sdk = SDK()
        SdkRegistry.register("shop-a", sdk)

        assertSame(sdk, received)
    }

    @Test
    fun `onNextRegister with null fires on the first registration of any shop`() {
        var received: SDK? = null
        SdkRegistry.onNextRegister(null) { received = it }

        val sdk = SDK()
        SdkRegistry.register("shop-whatever", sdk)

        assertSame(sdk, received)
    }

    @Test
    fun `onNextRegister ignores a registration for a different shop`() {
        var received: SDK? = null
        SdkRegistry.onNextRegister("shop-a") { received = it }

        SdkRegistry.register("shop-b", SDK())

        assertNull(received)
    }

    @Test
    fun `cancelling onNextRegister stops the callback firing on a later register`() {
        var received: SDK? = null
        val handle = SdkRegistry.onNextRegister("shop-a") { received = it }

        handle.cancel()
        SdkRegistry.register("shop-a", SDK())

        assertNull(received)
    }

    @Test
    fun `an awaiter fires once and not again on a second register`() {
        var count = 0
        SdkRegistry.onNextRegister("shop-a") { count++ }

        SdkRegistry.register("shop-a", SDK())
        SdkRegistry.register("shop-a", SDK())

        assertEquals(1, count)
    }

    // --- Rees46.awaitInstance ---

    @Test
    fun `awaitInstance fires immediately when the shop is already live`() {
        val sdk = SDK()
        SdkRegistry.register("shop-a", sdk)

        var received: SDK? = null
        Rees46.awaitInstance("shop-a") { received = it }

        assertSame(sdk, received)
    }

    @Test
    fun `awaitInstance without a shop fires immediately for the single live instance`() {
        val sdk = SDK()
        SdkRegistry.register("shop-a", sdk)

        var received: SDK? = null
        Rees46.awaitInstance { received = it }

        assertSame(sdk, received)
    }

    @Test
    fun `awaitInstance waits and then fires when the shop registers later`() {
        var received: SDK? = null
        Rees46.awaitInstance("shop-a") { received = it }
        assertNull(received)

        val sdk = SDK()
        SdkRegistry.register("shop-a", sdk)

        assertSame(sdk, received)
    }

    @Test
    fun `awaitInstance without a shop throws Ambiguous when several are live`() {
        SdkRegistry.register("shop-a", SDK())
        SdkRegistry.register("shop-b", SDK())

        assertThrows(AmbiguousSdkInstanceException::class.java) {
            Rees46.awaitInstance { /* never called */ }
        }
    }

    @Test
    fun `a cancelled awaitInstance does not fire when the shop registers later`() {
        var received: SDK? = null
        val handle = Rees46.awaitInstance("shop-a") { received = it }

        handle.cancel()
        SdkRegistry.register("shop-a", SDK())

        assertNull(received)
    }

    // --- Rees46.isInitialized ---

    @Test
    fun `isInitialized reflects whether a shop is live`() {
        assertFalse(Rees46.isInitialized("shop-a"))

        SdkRegistry.register("shop-a", SDK())

        assertTrue(Rees46.isInitialized("shop-a"))
        assertFalse(Rees46.isInitialized("shop-b"))
    }

    @Test
    fun `isInitialized without a shop is true only for a single live instance`() {
        assertFalse(Rees46.isInitialized())

        SdkRegistry.register("shop-a", SDK())
        assertTrue(Rees46.isInitialized())

        SdkRegistry.register("shop-b", SDK())
        assertFalse(Rees46.isInitialized())
    }
}
