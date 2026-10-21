package com.personalization.ui.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The in-process cache of widget responses: lifetime, size, shared requests and errors. */
class ResponseCacheTest {

    private var now = 0L
    private val cache = ResponseCache(ttlMillis = 1_000, capacity = 3, clock = { now })
    private val loads = mutableListOf<Pair<(String) -> Unit, (Int, String?) -> Unit>>()
    private val results = mutableListOf<String>()
    private val errors = mutableListOf<Int>()

    @Test
    fun `a repeated request within the lifetime is served from memory`() {
        request(key("a"))
        loads.last().first("first")

        request(key("a"))

        assertEquals(1, loads.size)
        assertEquals(listOf("first", "first"), results)
    }

    @Test
    fun `an answer older than the lifetime is fetched again`() {
        request(key("a"))
        loads.last().first("first")

        now += 1_000
        request(key("a"))

        assertEquals(2, loads.size)
    }

    @Test
    fun `shop, kind and parameters all make the key`() {
        request(key("a"))
        loads.last().first("a")

        request(ResponseCache.Key("other-shop", "recommendation", mapOf("code" to "a")))
        request(ResponseCache.Key("shop", "category", mapOf("code" to "a")))
        request(key("b"))

        assertEquals(4, loads.size)
    }

    @Test
    fun `beyond the capacity the oldest answer goes`() {
        listOf("a", "b", "c", "d").forEach { code ->
            request(key(code))
            loads.last().first(code)
        }

        request(key("b"))
        assertEquals(4, loads.size)
        request(key("a"))
        assertEquals(5, loads.size)
    }

    @Test
    fun `identical requests in flight share one call`() {
        request(key("a"))
        request(key("a"))

        assertEquals(1, loads.size)
        loads.single().first("shared")

        assertEquals(listOf("shared", "shared"), results)
    }

    @Test
    fun `errors reach every waiter and are not kept`() {
        request(key("a"))
        request(key("a"))
        loads.single().second(500, "server error")

        assertEquals(listOf(500, 500), errors)

        request(key("a"))
        assertEquals(2, loads.size)
    }

    @Test
    fun `a cancelled waiter hears nothing, but the answer is still kept`() {
        val handle = request(key("a"))
        handle.cancel()
        loads.single().first("late")

        assertTrue(results.isEmpty())
        request(key("a"))
        assertEquals(listOf("late"), results)
        assertEquals(1, loads.size)
    }

    @Test
    fun `a request that throws is an error and does not block the next one`() {
        cache.get<String>(key("a"), { _, _ -> throw IllegalStateException("boom") }, { results += it }, { code, _ -> errors += code })

        assertEquals(listOf(ResponseCache.ERROR_CODE), errors)
        request(key("a"))
        assertEquals(1, loads.size)
    }

    private fun key(code: String) = ResponseCache.Key("shop", "recommendation", mapOf("code" to code))

    private fun request(key: ResponseCache.Key) = cache.get<String>(
        key,
        load = { onSuccess, onError -> loads += onSuccess to onError },
        onSuccess = { results += it },
        onError = { code, _ -> errors += code }
    )
}
