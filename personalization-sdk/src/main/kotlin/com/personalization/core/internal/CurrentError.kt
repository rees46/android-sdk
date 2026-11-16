package com.personalization.core.internal

import com.personalization.core.SdkError

/**
 * The [SdkError] behind the old `onError(code, message)` running on this thread: set by [LegacyDelivery]
 * around the call, so it reaches a bridge's listener through the listeners the SDK wraps around it,
 * which forward on the same thread.
 */
internal object CurrentError {

    private val local = ThreadLocal<SdkError?>()

    fun get(): SdkError? = local.get()

    /** Runs [block] with [error] as the thread's error; the one before comes back after it. */
    fun <T> with(error: SdkError?, block: () -> T): T {
        val previous = local.get()
        local.set(error)
        try {
            return block()
        } finally {
            local.set(previous)
        }
    }
}
