package com.personalization.core.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pauses between repeats: 1 s doubling to 5 minutes, ±20 %, a server's Retry-After as it is. */
class BackoffTest {

    @Test
    fun `the pause doubles from a second and stops at five minutes`() {
        val backoff = Backoff(random = { 0.5 })

        assertEquals(
            listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 32_000L, 64_000L, 128_000L, 256_000L, 300_000L, 300_000L),
            (0..10).map { backoff.delayMs(it) },
        )
        assertEquals(300_000L, backoff.delayMs(Int.MAX_VALUE))
    }

    @Test
    fun `each pause is spread by 20 percent either way`() {
        val low = Backoff(random = { 0.0 })
        val high = Backoff(random = { 0.999_999 })

        assertEquals(800L, low.delayMs(0))
        assertEquals(240_000L, low.delayMs(20))
        assertTrue(high.delayMs(0) in 1_199L..1_200L)
        assertTrue(high.delayMs(20) in 359_999L..360_000L)
    }

    @Test
    fun `a Retry-After is waited as it is`() {
        val backoff = Backoff(random = { 0.0 })

        assertEquals(30_000L, backoff.delayMs(0, retryAfterMs = 30_000))
        assertEquals(3_600_000L, backoff.delayMs(5, retryAfterMs = 3_600_000))
        assertEquals(0L, backoff.delayMs(3, retryAfterMs = 0))
        assertEquals(800L, backoff.delayMs(0, retryAfterMs = -1))
    }
}
