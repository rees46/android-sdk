package com.personalization.demo.httplogger

import com.personalization.NetworkLogger
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Process-wide in-memory store of captured HTTP calls for the demo's HTTP logger screen.
 *
 * It is also a [NetworkLogger]: attach it once via `SDK.networkLogger = HttpLogStore` and every
 * SDK request/response is recorded here. Newest entries are kept first; the list is capped so a
 * long-running session can't grow without bound. Observers (the log screen) subscribe for updates.
 */
object HttpLogStore : NetworkLogger {

    private const val MAX_ENTRIES = 200

    private val entries = ArrayList<HttpLogEntry>()
    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private var nextId = 0L

    override fun onHttpCall(
        method: String,
        url: String,
        requestBody: String?,
        responseCode: Int,
        responseBody: String?,
        durationMs: Long,
        error: String?
    ) {
        val now = System.currentTimeMillis()
        synchronized(entries) {
            entries.add(
                0,
                HttpLogEntry(
                    id = nextId++,
                    timestamp = now,
                    method = method,
                    url = url,
                    requestBody = requestBody,
                    responseCode = responseCode,
                    responseBody = responseBody,
                    durationMs = durationMs,
                    error = error
                )
            )
            while (entries.size > MAX_ENTRIES) {
                entries.removeAt(entries.size - 1)
            }
        }
        notifyListeners()
    }

    /** Snapshot of all entries, newest first. */
    fun snapshot(): List<HttpLogEntry> = synchronized(entries) { entries.toList() }

    fun clear() {
        synchronized(entries) { entries.clear() }
        notifyListeners()
    }

    /** Subscribe to changes; returns an unsubscribe function. */
    fun subscribe(listener: () -> Unit): () -> Unit {
        listeners.add(listener)
        return { listeners.remove(listener) }
    }

    private fun notifyListeners() {
        listeners.forEach { it.invoke() }
    }
}
