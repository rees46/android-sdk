package com.personalization.core.internal

import com.personalization.Cancellable

/** Whether the device has a network, and when that changes: the queue sends when it comes back. */
internal interface ConnectivityMonitor {
    val isConnected: Boolean

    /** Calls [listener] with the new state on every change, until the returned handle is cancelled. */
    fun addListener(listener: (connected: Boolean) -> Unit): Cancellable
}
