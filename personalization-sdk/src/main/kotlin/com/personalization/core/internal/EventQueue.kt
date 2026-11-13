package com.personalization.core.internal

import com.personalization.Cancellable
import com.personalization.LogLevel
import com.personalization.api.OnApiCallbackListener
import com.personalization.core.SdkError
import com.personalization.core.SdkException
import com.personalization.core.SdkState
import com.personalization.logs.LogCodes
import com.personalization.logs.LogReasons
import com.personalization.logs.SdkLogs
import com.personalization.sdk.domain.models.NetworkMethod
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/**
 * An answer listener the SDK wraps around an event (the tracking methods): before the answer, the queue
 * says whether a popup in it may be shown (decision 2). The legacy core never sets it.
 */
internal abstract class EventAnswerListener : OnApiCallbackListener() {
    /** Null while a popup of the answer may be shown; otherwise why not, for `popup_not_shown`. */
    @Volatile
    var popupHeldBack: String? = null
}

/**
 * The new core's event queue (`core/queue.md`, decision 0002): `/push`, `/push/custom`, `track/stories`,
 * `track/slider` and `popup/showed` go through it, in order, and survive no network and a restart.
 *
 * - **Accepting.** [accept] answers a released instance with `notInitialized` and a refused shop with
 *   `shopBlocked` at once; otherwise it moves the session's last activity and hands the event to a
 *   writer that keeps the calls' order: an event is accepted once its file is on disk ([QueueStore]).
 *   A disk that refuses it sends it once, as a request.
 * - **Stamping.** The common fields — `did`, the `sid` of the moment — go on when the event is sent,
 *   with the stored tracking source as a `source` object on `/push` only and the time it was made as
 *   `time` on `/push/custom` only (the only path that reads it), unless the call set its own.
 * - **Sending.** One event at a time, oldest first, once the instance is ready and the session alive
 *   (a session whose window ended gets its `/init` first). Sending starts on [SdkState.READY], on the
 *   app's foreground and background, on the network's return, on [flush] and on a timer.
 * - **Answers.** `2xx` removes the event. No network waits for it and a timeout repeats it after a pause,
 *   neither counting as an attempt. `5xx`, `408`, `410`, `417`, `426`, `429` repeat it after a pause
 *   (the `Retry-After` of a `429` or `503`), and the 10th drops it. `402`, `403`, `404` pause the queue
 *   while `/init` checks the shop ([CoreInit.checkShopAndAwait]): a refusal clears the queue, a shop
 *   found fine sends the event once more, and a second such answer drops it. Other answers drop it at
 *   once. A dropped event does not hold the others.
 * - **Limits.** An event older than 7 days is dropped when its turn comes; at 1000 events the oldest go.
 *   Each drop is `queue_dropped` with its reason.
 * - **Listeners.** An old-style listener hears the event's fate once: the server's answer when it went,
 *   or the error that dropped it. A popup in the answer is shown only when the event went within 10 s
 *   of being made and the app is in the foreground (decision 2).
 *
 * [release] stops sending and keeps the files for the next launch; [clear] empties the queue (the
 * customer's deletion, the shop's refusal, which the queue hears from the state by itself).
 */
internal class EventQueue(
    private val shopId: String,
    private val store: QueueStore,
    private val init: CoreInit,
    private val state: InstanceState,
    private val send: suspend (NetworkMethod, JSONObject) -> HttpResult,
    private val connectivity: ConnectivityMonitor,
    private val lifecycle: AppLifecycle,
    /** The stored tracking source as `source` (`from`, `code`); null when there is none or it expired. */
    private val source: () -> JSONObject? = { null },
    /** Sends an event the disk would not take, once, as a request. */
    private val sendDirectly: (path: String, body: JSONObject, listener: OnApiCallbackListener?) -> Unit = { _, _, _ -> },
    dispatchers: CoreDispatchers = CoreDispatchers.DEFAULT,
    private val clock: Clock = Clock.SYSTEM,
    private val backoff: Backoff = Backoff(),
    /** How long a pass waits for the instance and its session before it waits for a trigger instead. */
    private val waitMs: Long = 10_000,
    /** At most this many events wait; tests make it small. */
    private val cap: Int = CAP,
) {

    private val handler = CoroutineExceptionHandler { _, throwable ->
        log(LogLevel.ERROR, LogCodes.SDK_LOG, "The event queue failed unexpectedly", throwable = throwable)
    }

    /** The writer and the answers that do not wait for a send: it outlives [release] to write what came before. */
    private val writing = CoroutineScope(SupervisorJob() + dispatchers.io + handler)

    /** The sending; [release] cancels it. */
    private val working = CoroutineScope(SupervisorJob() + dispatchers.io + handler)

    private val commands = Channel<Command>(Channel.UNLIMITED)
    private val wake = Channel<Unit>(Channel.CONFLATED)

    /** A trigger that may end a pause: not a new event, which waits for the pause like the others. */
    @Volatile
    private var urgent = false

    @Volatile
    private var released = false

    /** Released with its sending cancelled: nobody here will send what is still written. */
    @Volatile
    private var cancelled = false

    private val flushLock = Any()
    private val flushes = ArrayList<CompletableDeferred<Unit>>()
    private val subscriptions = ArrayList<Cancellable>()

    /** Failures in a row since the last event went: the index of the next pause. The worker's own. */
    private var failures = 0

    private val delivery = LegacyDelivery { level, code, message, throwable -> log(level, code, message, throwable = throwable) }

    private sealed interface Command {
        class Accept(val path: String, val body: JSONObject, val createdAt: Long, val listener: OnApiCallbackListener?) : Command
        class Clear(val reason: String, val error: LegacyError, val then: () -> Boolean, val done: CompletableDeferred<Boolean>?) : Command
        class Flush(val done: CompletableDeferred<Unit>) : Command
    }

    private sealed interface Outcome {
        /** Nothing to send, or not this queue's to send: wait for a trigger. */
        object Idle : Outcome

        /** The instance or its session is not there yet: wait for a trigger or the timer. */
        object Waiting : Outcome

        /** No network: wait for it, looking again now and then. */
        object Offline : Outcome

        class Pause(val delayMs: Long, val serverNamed: Boolean) : Outcome
    }

    /** Takes over the shop's queue and starts listening for the triggers. */
    fun start() {
        store.claim(this)
        val heard = listOf(
            connectivity.addListener { connected -> if (connected) kick(urgent = true) },
            lifecycle.addListener { kick(urgent = true) },
            init.session.addListener { _, _ -> kick(urgent = true) },
        )
        val kept = synchronized(subscriptions) { if (released) false else subscriptions.addAll(heard) }
        if (!kept) {
            heard.forEach { it.cancel() }
            return
        }
        writing.launch { for (command in commands) handle(command) }
        val following = working.launch {
            state.changes.collect { current ->
                when (current) {
                    SdkState.READY, SdkState.RETRYING -> kick(urgent = true)
                    SdkState.REFUSED -> clearLater(LogReasons.REFUSED, LegacyErrors.of(SdkError.ShopBlocked))
                    else -> Unit
                }
            }
        }
        // A release that lets the event in flight finish still stops following the state.
        synchronized(subscriptions) { if (released) following.cancel() else subscriptions += Cancellable { following.cancel() } }
        working.launch { work() }
    }

    /**
     * Takes an event of [path] with its own fields [params]. [listener] hears its fate once: at once
     * when the instance was released or the shop refused, otherwise after it went or was dropped.
     */
    fun accept(path: String, params: JSONObject, listener: OnApiCallbackListener?) {
        val error = when {
            released -> SdkError.NotInitialized
            state.current == SdkState.REFUSED -> SdkError.ShopBlocked
            else -> null
        }
        if (error != null) {
            if (listener != null) writing.launch { LegacyErrors.of(error).let { delivery.failure(listener, it.code, it.message) } }
            return
        }
        val createdAt = clock.nowMillis()
        init.session.extend(createdAt)
        if (commands.trySend(Command.Accept(path, params, createdAt, listener)).isFailure && listener != null) {
            writing.launch { LegacyErrors.of(SdkError.NotInitialized).let { delivery.failure(listener, it.code, it.message) } }
        }
    }

    /**
     * Sends what is in the queue now, without waiting for a trigger, and returns once each of those
     * events was tried: sent, dropped, or held by no network, the instance not being ready, the shop's
     * check or a server's `Retry-After`.
     *
     * @throws SdkException `notInitialized` after [release], `shopBlocked` for a refused shop.
     */
    suspend fun flush() {
        if (released) throw SdkException(SdkError.NotInitialized)
        if (state.current == SdkState.REFUSED) throw SdkException(SdkError.ShopBlocked)
        val done = CompletableDeferred<Unit>()
        if (commands.trySend(Command.Flush(done)).isFailure) throw SdkException(SdkError.NotInitialized)
        done.await()
    }

    /**
     * Empties the queue for [reason] and runs [then] while no event can be accepted: an event accepted
     * before this call goes, one accepted after it stays. The listeners waiting hear [error]. Returns
     * [then]'s result.
     */
    suspend fun clear(reason: String, error: LegacyError, then: () -> Boolean): Boolean {
        val done = CompletableDeferred<Boolean>()
        if (released || commands.trySend(Command.Clear(reason, error, then, done)).isFailure) return clearNow(reason, error, then)
        return withContext(NonCancellable) { done.await() }
    }

    /** [clear] without waiting, for the shop's refusal heard from the state. */
    private fun clearLater(reason: String, error: LegacyError) {
        commands.trySend(Command.Clear(reason, error, { true }, null))
    }

    /**
     * Stops sending; the events stay on disk for the next launch. With [cancel] the event in flight is
     * cancelled and the listeners of this queue's events hear `-1`; without, as when a push-only
     * instance gives way to the app's, the event in flight finishes and the listeners stay for the
     * queue that sends next. Events accepted before are still written.
     */
    fun release(cancel: Boolean = true) {
        val heard = synchronized(subscriptions) {
            if (released) return
            released = true
            subscriptions.toList().also { subscriptions.clear() }
        }
        heard.forEach { it.cancel() }
        commands.close()
        val next = store.leave(this)
        if (cancel) {
            cancelled = true
            working.cancel()
            val error = LegacyErrors.of(SdkError.Cancelled)
            store.takeListeners(this).forEach { delivery.failure(it.listener, error.code, error.message) }
        } else {
            kick(urgent = true)
        }
        completeFlushes()
        next?.kick(urgent = true)
    }

    /** Wakes the worker; an [urgent] trigger also ends a pause the server did not name. */
    internal fun kick(urgent: Boolean) {
        if (urgent) this.urgent = true
        wake.trySend(Unit)
    }

    private suspend fun handle(command: Command) {
        when (command) {
            is Command.Accept -> write(command)
            is Command.Clear -> command.done.let { done ->
                val result = clearNow(command.reason, command.error, command.then)
                done?.complete(result)
            }
            is Command.Flush -> {
                val owner = store.owner
                if (owner != null && owner !== this) {
                    owner.flushFromOther(command.done)
                } else {
                    synchronized(flushLock) { flushes += command.done }
                    kick(urgent = true)
                }
            }
        }
    }

    /** A flush asked of another queue of the shop: this one sends. */
    private fun flushFromOther(done: CompletableDeferred<Unit>) {
        if (released) {
            done.complete(Unit)
            return
        }
        synchronized(flushLock) { flushes += done }
        kick(urgent = true)
    }

    private fun write(accept: Command.Accept) {
        val dropped = ArrayList<Pair<QueuedEvent?, QueueStore.Waiting?>>()
        // After a cancelling release the event is still written, for the next launch, but nobody here
        // will send it: its listener hears -1 now.
        val keepListener = accept.listener.takeUnless { cancelled }
        val event = store.add(this, accept.path, accept.body, accept.createdAt, keepListener, cap, dropped)
        dropped.forEach { (old, waiting) -> dropped(old?.path, old?.attempts, LogReasons.OVERFLOW, waiting, OVERFLOWED) }
        if (keepListener == null && accept.listener != null) {
            LegacyErrors.of(SdkError.Cancelled).let { delivery.failure(accept.listener, it.code, it.message) }
        }
        if (event == null) {
            // Not accepted: the disk refused it (storage_failed). It goes once, as a request.
            sendDirectly(accept.path, accept.body, accept.listener)
            return
        }
        kick(urgent = false)
    }

    private fun clearNow(reason: String, error: LegacyError, then: () -> Boolean): Boolean {
        val (count, waiting, result) = store.clear(then)
        if (count > 0) {
            log(LogLevel.WARNING, LogCodes.QUEUE_DROPPED, "The queue was cleared", mapOf("reason" to reason, "count" to count))
        }
        waiting.forEach { delivery.failure(it.listener, error.code, error.message) }
        return result
    }

    private suspend fun work() {
        while (!released) {
            val tried = synchronized(flushLock) { flushes.toList().also { flushes.clear() } }
            val outcome = if (store.isOwner(this)) store.sending.withLock { pass() } else Outcome.Idle
            tried.forEach { it.complete(Unit) }
            when (outcome) {
                Outcome.Idle -> wake.receive()
                Outcome.Waiting -> withTimeoutOrNull(TIMER_MS) { wake.receive() }
                Outcome.Offline -> withTimeoutOrNull(NETWORK_RECHECK_MS) { wake.receive() }
                is Outcome.Pause -> pause(outcome)
            }
        }
    }

    /**
     * Waits out a pause. An urgent trigger (the network back, the foreground, a flush, ready) ends one
     * the backoff set; a server's `Retry-After` is waited in full, and a flush meanwhile is done at once:
     * nothing can go before.
     */
    private suspend fun pause(pause: Outcome.Pause) = coroutineScope {
        val over = launch { delay(pause.delayMs) }
        try {
            while (over.isActive && !released) {
                if (urgent && !pause.serverNamed) return@coroutineScope
                if (pause.serverNamed) completeFlushes()
                select<Unit> {
                    over.onJoin {}
                    wake.onReceive {}
                }
            }
        } finally {
            over.cancel()
        }
    }

    private fun completeFlushes() {
        val done = synchronized(flushLock) { flushes.toList().also { flushes.clear() } }
        done.forEach { it.complete(Unit) }
    }

    /** Sends events oldest first until the queue is empty or cannot send now. */
    private suspend fun pass(): Outcome {
        urgent = false
        while (true) {
            if (released || !store.isOwner(this) || state.current == SdkState.REFUSED) return Outcome.Idle
            val event = store.head() ?: return Outcome.Idle
            if (clock.nowMillis() - event.createdAt > TTL_MS) {
                drop(event, LogReasons.EXPIRED, EXPIRED)
                continue
            }
            if (!connectivity.isConnected) return Outcome.Offline
            // Ready, and when the session's window ended, the /init of a new one first.
            if (init.awaitUsable(waitMs) != null || !init.session.isAlive()) return Outcome.Waiting
            val sentAt = clock.nowMillis()
            val result = sendOne(event)
            // The answer is in: what it decides is done even if the queue is released meanwhile.
            val next = withContext(NonCancellable) { take(event, result, sentAt) }
            if (next != null) return next
        }
    }

    private suspend fun sendOne(event: QueuedEvent): HttpResult {
        val body = event.body
        if (event.path == PATH_PUSH) source()?.let { body.put(FIELD_SOURCE, it) }
        if (event.path == PATH_CUSTOM && !body.has(FIELD_TIME)) body.put(FIELD_TIME, event.createdAt / 1_000)
        return try {
            send(NetworkMethod.POST(event.path), body)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(LogLevel.WARNING, LogCodes.REQUEST_FAILED, "POST ${event.path}: no answer", mapOf("method" to "POST", "path" to event.path), e)
            HttpResult.Failure(SdkError.Network)
        }
    }

    /** What the answer to [event] does; null to go on with the next event. */
    private suspend fun take(event: QueuedEvent, result: HttpResult, sentAt: Long): Outcome? {
        when (result) {
            is HttpResult.Success -> return delivered(event, result.body, sentAt)
            is HttpResult.Failure -> return when (val error = result.error) {
                // A 2xx whose body is not JSON: the event went all the same.
                SdkError.Decoding -> delivered(event, JSONObject(), sentAt)
                SdkError.Network -> if (connectivity.isConnected) pause(null) else Outcome.Offline
                SdkError.Timeout -> pause(null)
                // A cancel the queue did not make (an interceptor): repeated like a timeout.
                SdkError.Cancelled -> if (released) Outcome.Idle else pause(null)
                is SdkError.Http -> answered(event, error.code, result)
                else -> {
                    drop(event, LogReasons.REJECTED, LegacyErrors.of(error))
                    null
                }
            }
        }
    }

    private suspend fun answered(event: QueuedEvent, code: Int, result: HttpResult.Failure): Outcome? {
        val error = LegacyErrors.of(SdkError.Http(code), result.errorBody)
        when {
            code in 500..599 || code in REPEATED_4XX -> {
                val attempts = event.attempts + 1
                if (attempts >= MAX_ATTEMPTS) {
                    drop(event, LogReasons.ATTEMPTS, error, attempts)
                    return null
                }
                store.update(event.copy(attempts = attempts))
                return pause(result.retryAfterMs.takeIf { code in RETRY_AFTER_CODES })
            }
            code in SHOP_REFUSALS -> {
                if (event.shopChecked) {
                    // The shop is fine, this service is not (an unpaid CRM for push/custom).
                    drop(event, LogReasons.SERVICE_REFUSED, error)
                    return null
                }
                return when (init.checkShopAndAwait(waitMs)) {
                    SdkState.READY -> {
                        store.update(event.copy(shopChecked = true))
                        null
                    }
                    // Cleared by the state's own news; a released queue sends nothing more.
                    SdkState.REFUSED, SdkState.NOT_INITIALIZED -> Outcome.Idle
                    // The check told nothing yet: the event waits, the check goes again later.
                    else -> pause(null)
                }
            }
            else -> {
                drop(event, LogReasons.REJECTED, error)
                return null
            }
        }
    }

    private fun pause(retryAfterMs: Long?): Outcome.Pause =
        Outcome.Pause(backoff.delayMs(failures++, retryAfterMs), serverNamed = retryAfterMs != null)

    /** The event went: it leaves the queue and its listener hears the answer. */
    private fun delivered(event: QueuedEvent, body: Any, sentAt: Long): Outcome? {
        failures = 0
        // Gone meanwhile (cleared, or dropped for room): its listener heard that already.
        val removed = store.remove(event.id) ?: return null
        val listener = removed.waiting?.listener ?: return null
        if (listener is EventAnswerListener) {
            listener.popupHeldBack = when {
                sentAt - event.createdAt > POPUP_WINDOW_MS -> LogReasons.LATE_EVENT
                !lifecycle.isForeground -> LogReasons.APP_IN_BACKGROUND
                else -> null
            }
        }
        delivery.success(listener, body)
        return null
    }

    private fun drop(event: QueuedEvent, reason: String, error: LegacyError, attempts: Int = event.attempts) {
        val removed = store.remove(event.id) ?: return
        dropped(event.path, attempts, reason, removed.waiting, error)
    }

    private fun dropped(path: String?, attempts: Int?, reason: String, waiting: QueueStore.Waiting?, error: LegacyError) {
        log(
            LogLevel.WARNING,
            LogCodes.QUEUE_DROPPED,
            "An event was dropped from the queue",
            buildMap {
                put("reason", reason)
                path?.let { put("path", it) }
                attempts?.let { put("attempts", it) }
            },
        )
        waiting?.let { delivery.failure(it.listener, error.code, error.message) }
    }

    private fun log(level: LogLevel, code: String, message: String, context: Map<String, Any?> = emptyMap(), throwable: Throwable? = null) =
        SdkLogs.log(level, code, message, shopId, context, throwable)

    internal companion object {
        const val PATH_PUSH = "push"
        const val PATH_CUSTOM = "push/custom"

        /** What the queue takes; the push events (`track/delivered` and the others) join with stage 6. */
        val PATHS: Set<String> = setOf(PATH_PUSH, PATH_CUSTOM, "track/stories", "track/slider", "popup/showed")

        const val TTL_MS = 7 * 24 * 60 * 60 * 1000L
        const val CAP = 1_000
        const val MAX_ATTEMPTS = 10
        const val POPUP_WINDOW_MS = 10_000L

        /** How often a queue that waits for the instance looks again by itself. */
        const val TIMER_MS = 60_000L

        /** How often a queue that waits for the network looks again by itself. */
        const val NETWORK_RECHECK_MS = 5 * 60 * 1000L

        private const val FIELD_SOURCE = "source"
        private const val FIELD_TIME = "time"
        private val SHOP_REFUSALS = setOf(402, 403, 404)
        private val REPEATED_4XX = setOf(408, 410, 417, 426, 429)
        private val RETRY_AFTER_CODES = setOf(429, 503)

        private val EXPIRED = LegacyError(LegacyErrors.NO_ANSWER, "The event expired in the queue")
        private val OVERFLOWED = LegacyError(LegacyErrors.NO_ANSWER, "The event was dropped: the queue was full")
    }
}
