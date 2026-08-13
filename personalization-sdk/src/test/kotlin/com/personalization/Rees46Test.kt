package com.personalization

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers the [Rees46] facade's routing (Release 3): getInstance for the single / ambiguous /
 * unknown cases, and that registerShops without eager init only records pending shops.
 *
 * Live instances are seeded straight into [SdkRegistry] with bare `SDK()` objects — that exercises
 * the routing without running the real `SDK.initialize`, which would make a network call. The lazy
 * materialization path (pending -> initialize) is therefore not asserted here; its decision is
 * covered by [InstanceResolverTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class Rees46Test {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        SdkRegistry.reset()
        Rees46.reset()
    }

    @After
    fun tearDown() {
        SdkRegistry.reset()
        Rees46.reset()
    }

    @Test
    fun `getInstance without a shop throws when nothing is registered`() {
        assertThrows(UnknownShopIdException::class.java) { Rees46.getInstance() }
    }

    @Test
    fun `getInstance for an unknown shop throws UnknownShopId`() {
        SdkRegistry.register("shop-a", SDK())

        assertThrows(UnknownShopIdException::class.java) { Rees46.getInstance("shop-x") }
    }

    @Test
    fun `getInstance returns the only instance when a single shop is registered`() {
        val sdk = SDK()
        SdkRegistry.register("shop-a", sdk)

        assertSame(sdk, Rees46.getInstance())
        assertSame(sdk, Rees46.getInstance("shop-a"))
    }

    @Test
    fun `getInstance without a shop throws Ambiguous when several shops are registered`() {
        SdkRegistry.register("shop-a", SDK())
        SdkRegistry.register("shop-b", SDK())

        assertThrows(AmbiguousShopException::class.java) { Rees46.getInstance() }
    }

    @Test
    fun `the default getInstance works for one shop and starts throwing once a second registers`() {
        // Integrator flow: init one shop, reach it with no shopId — fine. Init a second shop and the
        // bare getInstance() becomes ambiguous, so it must fail fast rather than silently pick one.
        // (register stands in for SDK.initialize, which would hit the network — see the class KDoc.)
        val first = SDK()
        SdkRegistry.register("shop-a", first)

        assertSame(first, Rees46.getInstance())

        SdkRegistry.register("shop-b", SDK())

        assertThrows(AmbiguousShopException::class.java) { Rees46.getInstance() }
        // The explicit id keeps working for both after the second registration.
        assertSame(first, Rees46.getInstance("shop-a"))
    }

    @Test
    fun `getInstance with an explicit shop resolves each instance under ambiguity`() {
        val a = SDK()
        val b = SDK()
        SdkRegistry.register("shop-a", a)
        SdkRegistry.register("shop-b", b)

        assertSame(a, Rees46.getInstance("shop-a"))
        assertSame(b, Rees46.getInstance("shop-b"))
    }

    @Test
    fun `registerShops without eager init records pending shops and creates no instances`() {
        Rees46.registerShops(
            context = context,
            configs = listOf(Rees46Config("shop-a"), Rees46Config("shop-b")),
            eagerInit = false
        )

        assertEquals(setOf("shop-a", "shop-b"), Rees46.pendingShopIds())
        assertEquals(0, SdkRegistry.count())
    }

    @Test
    fun `the ambiguous error names the registered shops`() {
        SdkRegistry.register("shop-a", SDK())
        SdkRegistry.register("shop-b", SDK())

        val message = assertThrows(AmbiguousShopException::class.java) {
            Rees46.getInstance()
        }.message.orEmpty()

        assertTrue(message, message.contains("shop-a") && message.contains("shop-b"))
    }
}
