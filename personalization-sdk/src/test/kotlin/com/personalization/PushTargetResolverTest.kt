package com.personalization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Covers the push routing rules behind [Rees46.handlePush] (Release 4). Pure logic, no dispatch, so
 * every case of "which shop does this push belong to" is pinned here.
 */
class PushTargetResolverTest {

    @Test
    fun `a payload shop_id that names a live instance routes to it`() {
        assertEquals(
            "shop-a",
            PushTargetResolver.resolve("shop-a", setOf("shop-a", "shop-b"))
        )
    }

    @Test
    fun `a payload shop_id with no live instance drops the push`() {
        assertNull(PushTargetResolver.resolve("shop-x", setOf("shop-a")))
    }

    @Test
    fun `no shop_id with a single live instance routes to it`() {
        assertEquals("shop-a", PushTargetResolver.resolve(null, setOf("shop-a")))
    }

    @Test
    fun `no shop_id with several live instances drops the push`() {
        assertNull(PushTargetResolver.resolve(null, setOf("shop-a", "shop-b")))
    }

    @Test
    fun `no shop_id with nothing live drops the push`() {
        assertNull(PushTargetResolver.resolve(null, emptySet()))
    }
}
