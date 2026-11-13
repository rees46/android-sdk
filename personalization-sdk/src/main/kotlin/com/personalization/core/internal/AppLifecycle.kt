package com.personalization.core.internal

import com.personalization.Cancellable

/**
 * Whether the app is in the foreground, and when that changes: going to the background flushes the
 * queue, coming back after two hours starts a new session.
 */
internal interface AppLifecycle {
    val isForeground: Boolean

    /** Calls [listener] with the new state on every change, until the returned handle is cancelled. */
    fun addListener(listener: (foreground: Boolean) -> Unit): Cancellable
}
