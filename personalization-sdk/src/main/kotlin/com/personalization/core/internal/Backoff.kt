package com.personalization.core.internal

import kotlin.random.Random

/**
 * The pauses between repeats (decision 0002): 1 s, 2 s, 4 s… up to 5 minutes, each spread by ±20 % so
 * that devices that failed together do not come back together. A `Retry-After` of the answer is taken
 * as it is.
 */
internal class Backoff(
    private val baseMs: Long = 1_000,
    private val maxMs: Long = 300_000,
    private val spread: Double = 0.2,
    /** A value in `[0, 1)`; tests pass a constant. */
    private val random: () -> Double = { Random.nextDouble() },
) {

    /** The pause before repeat number [attempt] (from 0), or [retryAfterMs] when the server named one. */
    fun delayMs(attempt: Int, retryAfterMs: Long? = null): Long {
        if (retryAfterMs != null && retryAfterMs >= 0) return retryAfterMs
        val doublings = attempt.coerceIn(0, MAX_DOUBLINGS)
        val pause = (baseMs shl doublings).coerceAtMost(maxMs)
        val factor = 1 + spread * (2 * random() - 1)
        return (pause * factor).toLong().coerceAtLeast(0)
    }

    private companion object {
        /** 2^20 seconds is far past the cap; more doublings would only overflow. */
        const val MAX_DOUBLINGS = 20
    }
}
