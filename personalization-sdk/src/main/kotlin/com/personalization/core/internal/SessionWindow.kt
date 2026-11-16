package com.personalization.core.internal

import com.personalization.Cancellable
import com.personalization.LogLevel
import com.personalization.logs.LogCodes
import com.personalization.logs.SdkLogs
import com.personalization.sdk.domain.repositories.UserSettingsRepository
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A shop's session on the new core (`core/session.md`, decision 0001): the `sid` the server hands out in
 * the `seance` of `/init`, and the time of the last activity, both kept in the shop's partition, so a
 * restart inside the window goes on with the same session.
 *
 * - The core never makes a `sid` of its own. A `sid` an earlier release made goes out once, with the
 *   `/init` of a window still alive, and the server's answer replaces it.
 * - The window is alive while there is a `sid` and the last activity is at most [windowMs] ago. An `/init`
 *   whose answer has no `seance` ([startWithoutSid]) starts a session without one: alive for its window
 *   all the same, so requests do not send `/init` after `/init` for it.
 * - Only `/init` ([start]) and the events of the queue ([extend]) move the last activity: the tracking events
 *   and, of the push events, a tap ([EventQueue.extendsSession]). Requests with a result do not, nor does a
 *   push's delivery or its notification shown or dismissed, as the server does not extend its own session
 *   on them either.
 *
 * Listeners hear every `/init` that answers: the `sid` it brings and whether it differs from the one
 * before ("the session changed", for the bridges), or, from an answer without a `seance`, the `sid` kept
 * (empty when none) and `changed` false — a queue waiting for the session goes on at once either way.
 * They are called on the thread that stored it and must not block.
 */
internal class SessionWindow(
    private val settings: UserSettingsRepository,
    private val storage: ShopStorage,
    private val clock: Clock = Clock.SYSTEM,
    val windowMs: Long = WINDOW_MS,
) {

    private val lock = Any()
    private val listeners = CopyOnWriteArrayList<(sid: String, changed: Boolean) -> Unit>()

    /** The last `sid` listeners heard of; a `sid` forgotten when its window ended still counts. */
    private var lastKnown: String = settings.getSid()

    /** A session `/init` started without a `sid`: alive for its window though it has none. In memory only. */
    private var withoutSid = false

    /** Whether the session is alive at [now]: a `sid`, and its last activity at most [windowMs] before. */
    fun isAlive(now: Long = clock.nowMillis()): Boolean = synchronized(lock) { alive(now) }

    private fun alive(now: Long): Boolean =
        (settings.getSid().isNotEmpty() || withoutSid) && now - settings.getSidLastActTime() <= windowMs

    /**
     * Ends a session whose window has passed: its `sid` and last activity go, so `/init` goes without
     * them and the server starts a new session. True when one ended.
     */
    fun forgetIfEnded(now: Long = clock.nowMillis()): Boolean = synchronized(lock) {
        if ((settings.getSid().isEmpty() && !withoutSid) || alive(now)) return false
        withoutSid = false
        storage.forgetSession()
        true
    }

    /**
     * An event of the queue accepted at [at]: the last activity moves there while the session is alive.
     * An ended session is not brought back: the event goes after the `/init` of a new one.
     */
    fun extend(at: Long = clock.nowMillis()) {
        synchronized(lock) {
            if (!alive(at) || at <= settings.getSidLastActTime()) return
            storage.preferences.edit().putLong(StorageKeys.SID_LAST_ACT, at).apply()
        }
    }

    /**
     * `/init` answered without a `seance`: a session alive from now, keeping the `sid` it has if its window
     * is alive, and without one otherwise. Listeners hear the `sid` kept, not as a change: no `sid` came,
     * but whoever waits for the session may go on.
     */
    fun startWithoutSid() {
        val kept = synchronized(lock) {
            val now = clock.nowMillis()
            if (settings.getSid().isEmpty() || !alive(now)) withoutSid = true
            storage.preferences.edit().putLong(StorageKeys.SID_LAST_ACT, now).apply()
            settings.getSid()
        }
        tell(kept, changed = false)
    }

    /** `/init` answered with [sid]: the session is [sid] and its last activity now. */
    fun start(sid: String) {
        val changed = synchronized(lock) {
            withoutSid = false
            storage.preferences.edit()
                .putString(StorageKeys.SID, sid)
                .putLong(StorageKeys.SID_LAST_ACT, clock.nowMillis())
                .apply()
            (sid != lastKnown).also { lastKnown = sid }
        }
        tell(sid, changed)
    }

    private fun tell(sid: String, changed: Boolean) {
        listeners.forEach { listener ->
            try {
                listener(sid, changed)
            } catch (e: Exception) {
                SdkLogs.log(LogLevel.ERROR, LogCodes.SDK_LOG, "A session listener failed", storage.layout.shopId, throwable = e)
            }
        }
    }

    /** Calls [listener] on each answer of `/init` ([start], [startWithoutSid]) until the handle is cancelled. */
    fun addListener(listener: (sid: String, changed: Boolean) -> Unit): Cancellable {
        listeners += listener
        return Cancellable { listeners -= listener }
    }

    internal companion object {
        /** Two hours without activity end a session. */
        const val WINDOW_MS = 2 * 60 * 60 * 1000L
    }
}
