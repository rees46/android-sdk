package com.personalization

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers [SdkRegistry], the internal routing state behind [SDK]'s companion. Release 1 of the
 * multi-instance work: the registry replaces the old `currentInstance` + `activeInstances` fields,
 * so these tests pin the single-instance behaviour (unchanged) and the shop-id resolution the
 * public multi-instance API will later build on.
 *
 * Bare `SDK()` instances are enough — the registry never calls into an instance, it only stores and
 * resolves references. Robolectric is needed because constructing `SDK` runs its field initializers.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SdkRegistryTest {

    @Before
    fun setUp() = SdkRegistry.reset()

    @After
    fun tearDown() = SdkRegistry.reset()

    @Test
    fun `a registered instance becomes the current default and is resolvable by shop id`() {
        val sdk = SDK()

        SdkRegistry.register(shopId = "shop-a", sdk = sdk)

        assertSame(sdk, SdkRegistry.current())
        assertSame(sdk, SdkRegistry.byShopId("shop-a"))
        assertEquals(1, SdkRegistry.count())
    }

    @Test
    fun `the last registered instance is the current default`() {
        val first = SDK()
        val second = SDK()

        SdkRegistry.register(shopId = "shop-a", sdk = first)
        SdkRegistry.register(shopId = "shop-b", sdk = second)

        assertSame(second, SdkRegistry.current())
        assertEquals(2, SdkRegistry.count())
    }

    @Test
    fun `each shop id resolves to its own instance`() {
        val a = SDK()
        val b = SDK()
        SdkRegistry.register(shopId = "shop-a", sdk = a)
        SdkRegistry.register(shopId = "shop-b", sdk = b)

        assertSame(a, SdkRegistry.byShopId("shop-a"))
        assertSame(b, SdkRegistry.byShopId("shop-b"))
    }

    @Test
    fun `an unknown shop id resolves to null`() {
        SdkRegistry.register(shopId = "shop-a", sdk = SDK())

        assertNull(SdkRegistry.byShopId("shop-x"))
    }

    @Test
    fun `re-registering the same instance keeps a single entry`() {
        val sdk = SDK()

        SdkRegistry.register(shopId = "shop-a", sdk = sdk)
        SdkRegistry.register(shopId = "shop-a", sdk = sdk)

        assertEquals(1, SdkRegistry.count())
    }

    @Test
    fun `re-registering a shop with a new instance evicts the superseded one from the fan-out set`() {
        val old = SDK()
        val new = SDK()
        SdkRegistry.register(shopId = "shop-a", sdk = old)
        SdkRegistry.register(shopId = "shop-a", sdk = new)

        assertEquals(1, SdkRegistry.count())
        assertSame(new, SdkRegistry.byShopId("shop-a"))
        val all = SdkRegistry.all()
        assertEquals(listOf(new), all)
    }

    @Test
    fun `onNextRegister fires immediately when the shop is already registered`() {
        val sdk = SDK()
        SdkRegistry.register(shopId = "shop-a", sdk = sdk)

        // Re-check under the lock: an awaiter subscribing after the registration must not be left
        // waiting for a signal that already fired.
        var received: SDK? = null
        SdkRegistry.onNextRegister("shop-a") { received = it }

        assertSame(sdk, received)
    }

    @Test
    fun `onNextRegister without a shop fires immediately when an instance is already current`() {
        val sdk = SDK()
        SdkRegistry.register(shopId = "shop-a", sdk = sdk)

        var received: SDK? = null
        SdkRegistry.onNextRegister(null) { received = it }

        assertSame(sdk, received)
    }

    @Test
    fun `onNextRegister waits and then fires on the next matching registration`() {
        var received: SDK? = null
        SdkRegistry.onNextRegister("shop-a") { received = it }
        assertNull(received)

        val sdk = SDK()
        SdkRegistry.register(shopId = "shop-a", sdk = sdk)

        assertSame(sdk, received)
    }

    @Test
    fun `a cancelled onNextRegister does not fire on a later registration`() {
        var received: SDK? = null
        val handle = SdkRegistry.onNextRegister("shop-a") { received = it }
        handle.cancel()

        SdkRegistry.register(shopId = "shop-a", sdk = SDK())

        assertNull(received)
    }

    @Test
    fun `all returns every registered instance for push fan-out`() {
        val a = SDK()
        val b = SDK()
        SdkRegistry.register(shopId = "shop-a", sdk = a)
        SdkRegistry.register(shopId = "shop-b", sdk = b)

        val all = SdkRegistry.all()

        assertEquals(2, all.size)
        assertTrue(all.contains(a))
        assertTrue(all.contains(b))
    }

    @Test
    fun `unregister drops the instance from the fan-out set and the shop mapping`() {
        val a = SDK()
        val b = SDK()
        SdkRegistry.register(shopId = "shop-a", sdk = a)
        SdkRegistry.register(shopId = "shop-b", sdk = b)

        SdkRegistry.unregister(a)

        assertEquals(1, SdkRegistry.count())
        assertNull(SdkRegistry.byShopId("shop-a"))
        assertSame(b, SdkRegistry.byShopId("shop-b"))
    }

    @Test
    fun `currentOrLazy creates a fallback instance when nothing is registered`() {
        assertNull(SdkRegistry.current())

        val lazy = SdkRegistry.currentOrLazy()

        assertNotNull(lazy)
        // The lazily created instance becomes current, so a second call returns the same one.
        assertSame(lazy, SdkRegistry.currentOrLazy())
    }

    @Test
    fun `currentOrLazy returns the registered instance instead of creating one`() {
        val sdk = SDK()
        SdkRegistry.register(shopId = "shop-a", sdk = sdk)

        assertSame(sdk, SdkRegistry.currentOrLazy())
    }
}
