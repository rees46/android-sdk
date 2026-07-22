package com.personalization

import com.personalization.InstanceResolver.Resolution
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers the resolution rules behind [Rees46.getInstance] (Release 3). Pure logic — no Android, no
 * SDK construction — so every branch of the single/ambiguous/uninitialized contract is pinned here.
 */
class InstanceResolverTest {

    private fun resolve(requested: String?, live: Set<String>, pending: Set<String>) =
        InstanceResolver.resolve(requested, live, pending)

    @Test
    fun `a requested shop with a live instance resolves to Existing`() {
        assertEquals(
            Resolution.Existing("shop-a"),
            resolve("shop-a", live = setOf("shop-a"), pending = emptySet())
        )
    }

    @Test
    fun `a requested shop that is only registered resolves to Pending`() {
        assertEquals(
            Resolution.Pending("shop-a"),
            resolve("shop-a", live = emptySet(), pending = setOf("shop-a"))
        )
    }

    @Test
    fun `a live registration wins over a stale pending one for the same shop`() {
        assertEquals(
            Resolution.Existing("shop-a"),
            resolve("shop-a", live = setOf("shop-a"), pending = setOf("shop-a"))
        )
    }

    @Test
    fun `a requested shop that is unknown resolves to NotInitialized`() {
        assertEquals(
            Resolution.NotInitialized,
            resolve("shop-x", live = setOf("shop-a"), pending = setOf("shop-b"))
        )
    }

    @Test
    fun `no shopId and nothing registered resolves to NotInitialized`() {
        assertEquals(
            Resolution.NotInitialized,
            resolve(null, live = emptySet(), pending = emptySet())
        )
    }

    @Test
    fun `no shopId and exactly one live shop resolves to that Existing`() {
        assertEquals(
            Resolution.Existing("shop-a"),
            resolve(null, live = setOf("shop-a"), pending = emptySet())
        )
    }

    @Test
    fun `no shopId and exactly one pending shop resolves to that Pending`() {
        assertEquals(
            Resolution.Pending("shop-a"),
            resolve(null, live = emptySet(), pending = setOf("shop-a"))
        )
    }

    @Test
    fun `no shopId and one live plus one pending shop is Ambiguous`() {
        assertEquals(
            Resolution.Ambiguous,
            resolve(null, live = setOf("shop-a"), pending = setOf("shop-b"))
        )
    }

    @Test
    fun `no shopId and two live shops is Ambiguous`() {
        assertEquals(
            Resolution.Ambiguous,
            resolve(null, live = setOf("shop-a", "shop-b"), pending = emptySet())
        )
    }

    @Test
    fun `the same shop live and pending counts once, so it is not Ambiguous`() {
        assertEquals(
            Resolution.Existing("shop-a"),
            resolve(null, live = setOf("shop-a"), pending = setOf("shop-a"))
        )
    }
}
