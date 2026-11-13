package com.personalization.core.internal

import kotlin.random.Random

/**
 * The pauses between repeats (decision 0002): 1 s, 2 s, 4 s… up to 5 minutes, each spread by ±20 % so
 * that devices that failed together do not come back together. The spread never takes a pause past
 * the 5 minutes. A `Retry-After` of the answer is waited too, but never less than a second and never
 * more than the longest pause: a server's `0`, a date already past or a value of days are all kept in
 * those bounds.
 */
internal class Backoff(
    private val baseMs: Long = 1_000,
    private val maxMs: Long = 300_000,
    private val spread: Double = 0.2,
    /** A value in `[0, 1)`; tests pass a constant. */
    private val random: () -> Double = { Random.nextDouble() },
) {

    /** The pause before repeat number [attempt] (from 0), or the one [retryAfterMs] names when the server named one. */
    fun delayMs(attempt: Int, retryAfterMs: Long? = null): Long {
        if (retryAfterMs != null && retryAfterMs >= 0) return retryAfterMs.coerceIn(MIN_RETRY_AFTER_MS, maxMs)
        val doublings = attempt.coerceIn(0, MAX_DOUBLINGS)
        val pause = (baseMs shl doublings).coerceAtMost(maxMs)
        val factor = 1 + spread * (2 * random() - 1)
        return (pause * factor).toLong().coerceIn(0, maxMs)
    }

    private companion object {
        /** 2^20 seconds is far past the cap; more doublings would only overflow. */
        const val MAX_DOUBLINGS = 20

        /** A `Retry-After` of `0` or of a moment already past still waits this long. */
        const val MIN_RETRY_AFTER_MS = 1_000L
    }
}
