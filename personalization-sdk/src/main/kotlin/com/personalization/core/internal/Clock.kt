package com.personalization.core.internal

/**
 * The time the core reads: the session window, retry pauses, the queue's limits, the source's
 * 48 hours. Tests pass their own to move time without waiting.
 */
internal fun interface Clock {
    fun nowMillis(): Long

    companion object {
        val SYSTEM: Clock = Clock { System.currentTimeMillis() }
    }
}
