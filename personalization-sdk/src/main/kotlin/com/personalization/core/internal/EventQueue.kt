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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference

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
 * `track/slider`, `popup/showed` and the push events (`track/delivered`, `track/opened`, `track/clicked`,
 * `track/closed`) go through it, in order, and survive no network and a restart.
 *
 * - **Accepting.** [accept] answers a released instance with `notInitialized`, a refused shop with
 *   `shopBlocked` and fields JSON cannot hold with `invalidArgument`, at once; otherwise it moves the
 *   session's last activity (a tracking event or a tap; a push's delivery, its notification shown or
 *   dismissed do not) and hands the event to a writer that keeps the calls' order: an event is accepted
 *   once its file is on disk ([QueueStore]), and the caller's `accepted` hears it then. A disk that
 *   refuses it sends it once, as a request, by the queue's rules: a tracking event that would start a
 *   session from the background is not accepted and does not go (`cancelled`).
 * - **Stamping.** The common fields — `did`, the `sid` of the moment, `segment`, `stream` — go on a copy
 *   when the event is sent ([stamp]), with the stored tracking source as a `source` object on `/push` only
 *   and the time it was made as `time` on `/push/custom` only (the only path that reads it), unless the
 *   call set its own. Numbers the older builders put in as text go as numbers ([EventNumbers]). Nothing
 *   stamped reaches the disk.
 * - **Sending.** One event at a time, by the one process of the app that holds the queue's file lock, in
 *   two lanes ([QueueLane]) that each keep their order, their pause after a failure and their count of
 *   failures: a lane that waits never holds the other. The push events need only the `did`: they neither
 *   wait for a session nor start one, and never ask `/init` anything. The tracking events need the
 *   instance ready and the session alive: a session whose window ended gets its `/init` first, from the
 *   foreground only. The queue of an instance brought up only for a push sends the push events alone. An
 *   event cleared while it waited (the customer's deletion) does not go. Sending starts on
 *   [SdkState.READY], on the app's foreground and background, on the network's return, on [flush] and on
 *   a timer.
 * - **Answers.** `2xx` removes the event. No network waits for it and a timeout repeats it after its lane's
 *   pause, neither counting as an attempt. `5xx`, `408`, `410`, `417`, `426`, `429` repeat it after its
 *   lane's pause (the `Retry-After` of a `429` or `503`), and the 10th drops it. A push event answered `402`
 *   or `403` is dropped as the service's refusal (`service_refused`), `404` as `rejected`. A tracking event
 *   answered `402`, `403`, `404` holds its lane while `/init` checks the shop
 *   ([CoreInit.checkShopAndAwait]), from the foreground only: a refusal clears the queue, a shop found fine
 *   by a new answer sends the event once more, and a second such answer drops it; a check that may not go
 *   yet holds the event until it may. Other answers drop it at once. A dropped event does not hold the
 *   others. A disk that fails to read an event pauses the queue, and one that fails to rewrite one pauses
 *   its lane, instead of sending it again at once.
 * - **Limits.** An event older than 7 days is dropped when its turn comes; at 1000 events the oldest go.
 *   Each drop is `queue_dropped` with its reason. A queue the shop's refusal cleared tells its events'
 *   listeners `cancelled`.
 * - **Listeners.** An old-style listener hears the event's fate once: the server's answer when it went,
 *   or the error that dropped it. A popup in the answer is shown only when the event went within 10 s
 *   of being made and the app is in the foreground (decision 2).
 *
 * [release] stops sending and keeps the files for the next launch; [clear] empties the queue (the
 * customer's deletion, the shop's refusal, which the queue hears from the state by itself). Neither the
 * writer nor the sender stops on an unexpected exception: it is logged and the next command or event goes.
 */
internal class EventQueue(
    private val shopId: String,
    private val store: QueueStore,
    private val init: CoreInit,
    private val state: InstanceState,
    /** Sends an event whose fields are all on it: it adds none. */
    private val send: suspend (NetworkMethod, JSONObject) -> HttpResult,
    private val connectivity: ConnectivityMonitor,
    private val lifecycle: AppLifecycle,
    /** The stored tracking source as `source` (`from`, `code`); null when there is none or it expired. */
    private val source: () -> JSONObject? = { null },
    /** Puts the common fields on an event's copy before it goes. */
    private val stamp: (JSONObject) -> JSONObject = { it },
    /** Sends an event the disk would not take, once, as a request; the common fields are its to add. */
    private val sendDirectly: (path: String, body: JSONObject, listener: OnApiCallbackListener?) -> Unit = { _, _, _ -> },
    dispatchers: CoreDispatchers = CoreDispatchers.DEFAULT,
    private val clock: Clock = Clock.SYSTEM,
    private val backoff: Backoff = Backoff(),
    /** How long a pass waits for the instance and its session before it waits for a trigger instead. */
    private val waitMs: Long = 10_000,
    /** At most this many events wait; tests make it small. */
    private val cap: Int = CAP,
    /**
     * The queue of an instance brought up only for a push ([InitSetup.pushOnly]): it sends the push events
     * and leaves the others on disk for the app's own instance, which starts their session.
     */
    private val pushEventsOnly: Boolean = false,
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

    /** The writer: while it runs, a command sent to it is handled. */
    @Volatile
    private var writer: Job? = null

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

    /**
     * Passes in a row the disk failed to read the queue or that failed unexpectedly: the index of the
     * queue's next pause. The worker's own.
     */
    private var stalls = 0

    private val pushLane = LanePause()
    private val trackingLane = LanePause()

    /**
     * The tracking event answered `402`, `403` or `404` in the background, with what it was answered: the
     * shop's check waits for the foreground. The worker's own.
     */
    private var checkDue: Pair<String, LegacyError>? = null

    private val delivery = LegacyDelivery { level, code, message, throwable -> log(level, code, message, throwable = throwable) }

    private sealed interface Command {
        class Accept(
            val path: String,
            val body: JSONObject,
            val createdAt: Long,
            val listener: OnApiCallbackListener?,
            val accepted: ((SdkError?) -> Unit)?,
        ) : Command

        class Clear(val reason: String, val error: LegacyError, val then: () -> Boolean, val done: CompletableDeferred<Boolean>?) : Command
        class Flush(val done: CompletableDeferred<Unit>) : Command

        /** Done once every command sent before it was handled. */
        class Barrier(val done: CompletableDeferred<Unit>) : Command
    }

    private sealed interface Outcome {
        /** Nothing to send, or not this queue's to send: wait for a trigger. */
        object Idle : Outcome

        /** The instance or its session is not there yet, or another process sends: wait for a trigger or the timer. */
        object Waiting : Outcome

        /** No network: wait for it, looking again now and then. */
        object Offline : Outcome

        /** The disk failed to read the queue, or the pass failed unexpectedly: both lanes wait, unless an urgent trigger ends it. */
        class Pause(val delayMs: Long) : Outcome
    }

    /** What an answer or a check did to the event's lane; [Stop] ends the pass. */
    private sealed interface Step {
        /** The lane goes on with its next event. */
        object Next : Step

        /** The lane waits (a pause, the shop's check): the other one goes on. */
        object Held : Step

        class Stop(val outcome: Outcome) : Step
    }

    /**
     * One lane's pause after a failure and its failures in a row, the index of its next pause. The worker
     * sets and ends it; the pause's own end only clears it and wakes the worker.
     */
    private inner class LanePause {
        var failures = 0

        private val current = AtomicReference<Job?>(null)

        @Volatile
        private var serverNamed = false

        val isPaused: Boolean get() = current.get() != null

        /** A pause of the backoff after one more failure, or the server's `Retry-After` ([retryAfterMs]). */
        fun afterFailure(retryAfterMs: Long?) = pause(backoff.delayMs(failures++, retryAfterMs), serverNamed = retryAfterMs != null)

        /** A pause of [delayMs]; one [serverNamed] no trigger ends. */
        fun pause(delayMs: Long, serverNamed: Boolean) {
            this.serverNamed = serverNamed
            val job = working.launch(start = CoroutineStart.LAZY) { delay(delayMs) }
            job.invokeOnCompletion { if (current.compareAndSet(job, null)) kick(urgent = false) }
            current.getAndSet(job)?.cancel()
            job.start()
        }

        /** An urgent trigger: a pause of the backoff ends, a server's `Retry-After` is waited in full. */
        fun cut() {
            if (!serverNamed) current.getAndSet(null)?.cancel()
        }

        fun succeeded() {
            failures = 0
        }
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
        writer = writing.launch {
            try {
                for (command in commands) handleSafely(command)
            } finally {
                // Ended for whatever reason: nothing sent to it later may wait for it.
                commands.close()
                while (true) settle(commands.tryReceive().getOrNull() ?: break, null)
            }
        }
        val following = working.launch {
            state.changes.collect { current ->
                when (current) {
                    SdkState.READY, SdkState.RETRYING -> kick(urgent = true)
                    // The events were dropped, not refused: their listeners hear `cancelled` (core/queue.md, «Пределы»).
                    SdkState.REFUSED -> clearLater(LogReasons.REFUSED, CLEARED_REFUSED)
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
     * when the instance was released, the shop refused or the fields are not JSON, otherwise after it went
     * or was dropped. [accepted] hears, once, null as soon as the event is written (or, refused by the
     * disk, sent once as a request), or the error that refused it. The method itself returns at once.
     */
    fun accept(path: String, params: JSONObject, listener: OnApiCallbackListener?, accepted: ((SdkError?) -> Unit)? = null) {
        // A copy of the fields as they are now, which also tells fields JSON cannot hold (NaN, Infinity).
        val body = snapshot(params)
        val error = when {
            released -> SdkError.NotInitialized
            state.current == SdkState.REFUSED -> SdkError.ShopBlocked
            body == null -> SdkError.InvalidArgument("The event's fields cannot be written as JSON")
            else -> null
        }
        if (error != null || body == null) {
            if (error is SdkError.InvalidArgument) {
                log(LogLevel.WARNING, LogCodes.INVALID_ARGUMENT, "An event was refused: its fields are not JSON", mapOf("reason" to REASON_INVALID_JSON, "path" to path))
            }
            refuse(error ?: SdkError.NotInitialized, listener, accepted)
            return
        }
        val createdAt = clock.nowMillis()
        // Tracking events move the session's last activity; of the push events only a tap, which the server
        // counts too: a delivery, a notification shown or dismissed in the background extend nothing.
        if (extendsSession(path)) init.session.extend(createdAt)
        if (writer?.isActive != true || commands.trySend(Command.Accept(path, body, createdAt, listener, accepted)).isFailure) {
            refuse(SdkError.NotInitialized, listener, accepted)
        }
    }

    private fun snapshot(params: JSONObject): JSONObject? = try {
        // JSONObject.toString() gives null for a value JSON cannot hold.
        val text: String? = params.toString()
        text?.let { JSONObject(it) }
    } catch (e: Exception) {
        null
    }

    private fun refuse(error: SdkError, listener: OnApiCallbackListener?, accepted: ((SdkError?) -> Unit)?) {
        if (listener == null && accepted == null) return
        writing.launch {
            listener?.let { delivery.failure(it, LegacyErrors.of(error)) }
            accepted?.let { tell(it, error) }
        }
    }

    /** Hands [accepted] its answer; one that throws is logged. */
    private fun tell(accepted: (SdkError?) -> Unit, error: SdkError?) {
        try {
            accepted(error)
        } catch (e: Exception) {
            log(LogLevel.ERROR, LogCodes.CALLBACK_FAILED, "An event's acceptance listener threw", throwable = e)
        }
    }

    /**
     * Sends what is in the queue now, without waiting for a trigger, and returns once each of those
     * events was tried: sent, dropped, or held by no network, the instance not being ready, the shop's
     * check or a server's `Retry-After`. The pauses of the backoff end, in both lanes: the events waiting in
     * them go again at once; a server's `Retry-After` does not. A refused shop's queue is empty: it returns
     * at once, without an error (core/init.md).
     *
     * @throws SdkException `notInitialized` after [release].
     */
    suspend fun flush() {
        if (released) throw SdkException(SdkError.NotInitialized)
        if (state.current == SdkState.REFUSED) return
        val done = CompletableDeferred<Unit>()
        if (writer?.isActive != true || commands.trySend(Command.Flush(done)).isFailure) throw SdkException(SdkError.NotInitialized)
        done.await()
    }

    /**
     * Returns once every event accepted before the call is written to disk, or refused: the writer handled
     * what came before. At once without a writer (released).
     */
    suspend fun awaitWrites() {
        val done = CompletableDeferred<Unit>()
        if (writer?.isActive != true || commands.trySend(Command.Barrier(done)).isFailure) return
        done.await()
    }

    /**
     * Empties the queue for [reason] and runs [then] while no event can be accepted: an event accepted
     * before this call goes, one accepted after it stays. The listeners waiting hear [error]. Returns
     * [then]'s result. Released, it takes no command: it waits for the writer to write what was accepted
     * before it, then clears here.
     */
    suspend fun clear(reason: String, error: LegacyError, then: () -> Boolean): Boolean {
        val done = CompletableDeferred<Boolean>()
        if (released || writer?.isActive != true || commands.trySend(Command.Clear(reason, error, then, done)).isFailure) {
            // The writer drains what was accepted before the release; cleared first, that would outlive the clear.
            withContext(NonCancellable) { writer?.join() }
            return clearNow(reason, error, then)
        }
        return withContext(NonCancellable) { done.await() }
    }

    /** [clear] without waiting, for the shop's refusal heard from the state. */
    private fun clearLater(reason: String, error: LegacyError) {
        commands.trySend(Command.Clear(reason, error, { true }, null))
    }

    /**
     * Stops sending; the events stay on disk for the next launch. With [cancel] the sending stops at once
     * and the listeners of this queue's events hear `-1`; the request of the event in flight still ends,
     * holding the queue until then so that the queue made next does not send its event again, and an
     * answer that says it went takes the event off the disk. Without, as when a push-only instance gives
     * way to the app's, the event in flight finishes and the listeners stay for the queue that sends next.
     * Events accepted before are still written; a flush asked before it and not handled yet ends with
     * `notInitialized`, and one a pass had taken ends.
     */
    fun release(cancel: Boolean = true) {
        val heard = synchronized(subscriptions) {
            if (released) return
            // Under the flushes' lock too: a flush handed over after this sees it and ends at once.
            synchronized(flushLock) { released = true }
            subscriptions.toList().also { subscriptions.clear() }
        }
        heard.forEach { it.cancel() }
        commands.close()
        val next = store.leave(this)
        if (cancel) {
            cancelled = true
            working.cancel()
            val error = LegacyErrors.of(SdkError.Cancelled)
            store.takeListeners(this).forEach { delivery.failure(it.listener, error) }
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

    /** One command; nothing it throws stops the writer, and whoever waits for it still hears an end. */
    private fun handleSafely(command: Command) {
        try {
            handle(command)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(LogLevel.ERROR, LogCodes.SDK_LOG, "A command of the event queue failed", throwable = e)
            settle(command, e)
        }
    }

    /** Ends [command], not handled or failed with [failure]: whoever waits for it hears. */
    private fun settle(command: Command, failure: Exception?) {
        when (command) {
            is Command.Accept -> {
                val error = if (failure == null) SdkError.NotInitialized else SdkError.InvalidArgument("The event could not be written")
                command.listener?.let { delivery.failure(it, LegacyErrors.of(error)) }
                command.accepted?.let { tell(it, error) }
            }
            // Failed midway: reported as not complete. Never handled: its work is done here.
            is Command.Clear -> command.done?.complete(
                failure == null && try {
                    command.then()
                } catch (e: Exception) {
                    log(LogLevel.ERROR, LogCodes.STORAGE_FAILED, "The customer's data could not be deleted", throwable = e)
                    false
                }
            )
            is Command.Flush -> command.done.completeExceptionally(SdkException(SdkError.NotInitialized))
            is Command.Barrier -> command.done.complete(Unit)
        }
    }

    private fun handle(command: Command) {
        when (command) {
            is Command.Accept -> write(command)
            is Command.Clear -> command.done.let { done ->
                val result = clearNow(command.reason, command.error, command.then)
                done?.complete(result)
            }
            is Command.Flush -> {
                if (released) {
                    // Asked before the release, handled after it: nothing here sends any more.
                    command.done.completeExceptionally(SdkException(SdkError.NotInitialized))
                    return
                }
                // A flush asked of another queue of the shop: the one that sends takes it.
                (store.owner ?: this).addFlush(command.done)
            }
            is Command.Barrier -> command.done.complete(Unit)
        }
    }

    /** Hands [done] to the next pass; a released queue sends nothing more, so it ends at once. */
    private fun addFlush(done: CompletableDeferred<Unit>) {
        val added = synchronized(flushLock) { if (released) false else flushes.add(done) }
        if (added) kick(urgent = true) else done.complete(Unit)
    }

    private fun write(accept: Command.Accept) {
        val dropped = ArrayList<Pair<QueuedEvent?, QueueStore.Waiting?>>()
        // After a cancelling release the event is still written, for the next launch, but nobody here
        // will send it: its listener hears -1 now.
        val keepListener = accept.listener.takeUnless { cancelled }
        val event = store.add(this, accept.path, accept.body, accept.createdAt, keepListener, cap, dropped)
        dropped.forEach { (old, waiting) -> dropped(old?.path, old?.attempts, LogReasons.OVERFLOW, waiting, OVERFLOWED) }
        if (keepListener == null && accept.listener != null) {
            delivery.failure(accept.listener, LegacyErrors.of(SdkError.Cancelled))
        }
        if (event == null && mayNotStartSession(accept.path)) {
            // The disk refused it (storage_failed), and sent now it would start a session from the background,
            // which the queue never does: not accepted, nothing goes.
            log(LogLevel.WARNING, LogCodes.REQUEST_FAILED, "POST ${accept.path}: not sent: the disk refused it and no session starts from the background",
                mapOf("method" to "POST", "path" to accept.path, "reason" to LogReasons.NOT_READY))
            accept.listener?.let { delivery.failure(it, NOT_KEPT) }
            accept.accepted?.let { tell(it, NOT_KEPT.error) }
            return
        }
        accept.accepted?.let { tell(it, null) }
        if (event == null) {
            // Not accepted: the disk refused it (storage_failed). It goes once, as a request, with what
            // the queue would have put on it, by the queue's rules.
            sendDirectly(accept.path, withOwnFields(accept.path, JSONObject(accept.body.toString()), accept.createdAt), accept.listener)
            return
        }
        // The queue that sends: this one, or the one made after it.
        (store.owner ?: this).kick(urgent = false)
    }

    /**
     * Whether an event of [path] the disk refused would start a session from the background if it went now:
     * a tracking event whose session ended while the app is in the background. The queue holds such an event
     * until the foreground; one that is not kept cannot wait.
     */
    private fun mayNotStartSession(path: String): Boolean =
        QueueLane.of(path) == QueueLane.TRACKING && !lifecycle.isForeground && !init.session.isAlive()

    private fun clearNow(reason: String, error: LegacyError, then: () -> Boolean): Boolean {
        val (count, waiting, result) = store.clear(then)
        if (count > 0) {
            log(LogLevel.WARNING, LogCodes.QUEUE_DROPPED, "The queue was cleared", mapOf("reason" to reason, "count" to count))
        }
        waiting.forEach { delivery.failure(it.listener, error) }
        return result
    }

    private suspend fun work() {
        while (!released) {
            val tried = synchronized(flushLock) { flushes.toList().also { flushes.clear() } }
            val outcome = try {
                if (store.isOwner(this)) sendingPass() else Outcome.Idle
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Never a tight loop: the next pass comes after a pause.
                log(LogLevel.ERROR, LogCodes.SDK_LOG, "Sending the event queue failed", throwable = e)
                stall()
            } finally {
                // The flushes this pass took end however it ended: done, failed, or cut off by a release.
                tried.forEach { it.complete(Unit) }
            }
            when (outcome) {
                Outcome.Idle -> wake.receive()
                Outcome.Waiting -> withTimeoutOrNull(TIMER_MS) { wake.receive() }
                Outcome.Offline -> withTimeoutOrNull(NETWORK_RECHECK_MS) { wake.receive() }
                is Outcome.Pause -> pause(outcome)
            }
        }
    }

    /**
     * Waits out the queue's pause after a failed pass ([stall]); an urgent trigger (the network back, the
     * foreground, a flush, ready) ends it.
     */
    private suspend fun pause(pause: Outcome.Pause) = coroutineScope {
        val over = launch { delay(pause.delayMs) }
        try {
            while (over.isActive && !released) {
                if (urgent) return@coroutineScope
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

    /**
     * Whether the request of the event in flight took over the pass's hold of the queue: a cancelling
     * [release] cut the pass off while the request runs ([sendOne]).
     */
    private class Hold {
        @Volatile
        var handedOver = false
    }

    /**
     * One pass holding [QueueStore.sending] and, while this process holds the queue's file lock, sending;
     * when another process holds it, that one sends. A request a cancelling [release] cut off keeps both
     * until it ends, so neither the queue made after this one nor another process sends its event again
     * meanwhile.
     */
    private suspend fun sendingPass(): Outcome {
        store.sending.lock()
        val hold = Hold()
        try {
            if (!store.lockForSending()) return Outcome.Waiting
            try {
                return pass(hold)
            } finally {
                if (!hold.handedOver) store.unlockForSending()
            }
        } finally {
            if (!hold.handedOver) store.sending.unlock()
        }
    }

    /**
     * Sends events oldest first until the queue is empty or cannot send now, in two lanes that each keep
     * their order: the push events, which need only a `did`, and the tracking events, which need a live
     * session. A lane that waits — its pause after a failure, its session, the shop's check — does not hold
     * the other: the pass goes on with that one alone. An urgent trigger ends both lanes' pauses of the
     * backoff first.
     */
    private suspend fun pass(hold: Hold): Outcome {
        if (urgent) {
            urgent = false
            pushLane.cut()
            trackingLane.cut()
        }
        var pushHeld = pushLane.isPaused
        var trackingHeld = pushEventsOnly || trackingLane.isPaused
        // The tracking lane waits for a session: the timer looks again.
        var waiting = false
        while (true) {
            if (released || !store.isOwner(this) || state.current == SdkState.REFUSED) return Outcome.Idle
            // A clear gives the lock back: it is taken again for what came after it.
            if (!store.lockForSending()) return Outcome.Waiting
            val idle = if (waiting) Outcome.Waiting else Outcome.Idle
            if (pushHeld && trackingHeld) return idle
            val generation = store.generation
            val unreadable = ArrayList<QueueStore.Waiting>()
            val head = store.head(unreadable) { lane -> if (lane == QueueLane.PUSH) pushHeld else trackingHeld }
            unreadable.forEach { delivery.failure(it.listener, UNREADABLE) }
            val event = when (head) {
                QueueStore.Head.Empty -> return idle
                // The disk failed to read it: later, not again at once.
                QueueStore.Head.Unavailable -> return stall()
                is QueueStore.Head.Event -> head.event
            }
            if (clock.nowMillis() - event.createdAt > TTL_MS) {
                drop(event, LogReasons.EXPIRED, EXPIRED)
                continue
            }
            if (!connectivity.isConnected) return Outcome.Offline
            val push = event.lane == QueueLane.PUSH
            val due = checkDue?.takeIf { it.first == event.id }
            if (push) {
                // A push's event needs a did and no session: the server reads no sid in it. One that came
                // after the window ended starts no /init.
                if (init.awaitReady(waitMs) != null) return Outcome.Waiting
            } else if (due != null) {
                // Answered 402, 403 or 404 in the background: the shop's check goes from the foreground only,
                // and the tracking lane waits for it. The push lane goes meanwhile.
                if (!lifecycle.isForeground) {
                    trackingHeld = true
                    continue
                }
                checkDue = null
                when (val step = checkShop(event, due.second)) {
                    Step.Next -> Unit
                    Step.Held -> trackingHeld = true
                    is Step.Stop -> return step.outcome
                }
                continue
            } else if (!init.session.isAlive()) {
                // The session's window ended: a new one starts from the foreground only — its /init goes,
                // and its answer wakes the queue — never from the background. The push lane goes meanwhile.
                if (lifecycle.isForeground) init.renewSession()
                trackingHeld = true
                waiting = true
                continue
            } else if (init.awaitReady(waitMs) != null) {
                // Not ready: neither lane can go.
                return Outcome.Waiting
            } else if (!init.session.isAlive()) {
                // The window ended while it waited: the head is looked at again, as a session's that ended.
                continue
            }
            // Cleared while it waited (the customer was deleted): it does not go under the next one.
            val body = store.ifCurrent(generation, event.id) { stamped(event) } ?: continue
            val sentAt = clock.nowMillis()
            val result = sendOne(event, body, hold)
            // The answer is in: what it decides is done even if the queue is released meanwhile.
            when (val step = withContext(NonCancellable) { take(event, result, sentAt) }) {
                Step.Next -> Unit
                Step.Held -> if (push) pushHeld = true else trackingHeld = true
                is Step.Stop -> return step.outcome
            }
        }
    }

    /** What goes for [event]: a copy of its fields with the queue's and the common ones. */
    private fun stamped(event: QueuedEvent): JSONObject =
        stamp(withOwnFields(event.path, EventNumbers.normalize(event.path, JSONObject(event.body.toString())), event.createdAt))

    /** The stored source on `/push` and the time the event was made on `/push/custom`, unless it has its own. */
    private fun withOwnFields(path: String, body: JSONObject, createdAt: Long): JSONObject {
        if (path == PATH_PUSH) source()?.let { body.put(FIELD_SOURCE, it) }
        if (path == PATH_CUSTOM && !body.has(FIELD_TIME)) body.put(FIELD_TIME, createdAt / 1_000)
        return body
    }

    /**
     * Sends [event]. The request runs apart from the sending: a [release] that cancels the sending lets it
     * end, holding [hold]'s claim on the queue — the sending's lock and the file lock — until then, so the
     * event is not sent twice at once; an answer that says it went still takes the event off the disk, so it
     * does not go again later either.
     */
    private suspend fun sendOne(event: QueuedEvent, body: JSONObject, hold: Hold): HttpResult {
        val call = writing.async { request(event, body) }
        try {
            return call.await()
        } catch (e: CancellationException) {
            hold.handedOver = true
            writing.launch {
                try {
                    val result = call.await()
                    if (result is HttpResult.Success || (result as? HttpResult.Failure)?.error == SdkError.Decoding) store.remove(event.id)
                } finally {
                    store.unlockForSending()
                    store.sending.unlock()
                }
            }
            throw e
        }
    }

    private suspend fun request(event: QueuedEvent, body: JSONObject): HttpResult = try {
        send(NetworkMethod.POST(event.path), body)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log(LogLevel.WARNING, LogCodes.REQUEST_FAILED, "POST ${event.path}: no answer", mapOf("method" to "POST", "path" to event.path), e)
        HttpResult.Failure(SdkError.Network)
    }

    /** The pauses of [event]'s lane. */
    private fun laneOf(event: QueuedEvent): LanePause = if (event.lane == QueueLane.PUSH) pushLane else trackingLane

    /** What the answer to [event] does to it and to its lane. */
    private suspend fun take(event: QueuedEvent, result: HttpResult, sentAt: Long): Step {
        val lane = laneOf(event)
        return when (result) {
            is HttpResult.Success -> delivered(event, result.body, sentAt)
            is HttpResult.Failure -> when (val error = result.error) {
                // A 2xx whose body is not JSON: the event went all the same.
                SdkError.Decoding -> delivered(event, JSONObject(), sentAt)
                SdkError.Network -> if (connectivity.isConnected) held(lane, null) else Step.Stop(Outcome.Offline)
                SdkError.Timeout -> held(lane, null)
                // A cancel the queue did not make (an interceptor): repeated like a timeout.
                SdkError.Cancelled -> if (released) Step.Stop(Outcome.Idle) else held(lane, null)
                is SdkError.Http -> answered(event, error.code, result)
                else -> {
                    drop(event, LogReasons.REJECTED, LegacyErrors.of(error))
                    Step.Next
                }
            }
        }
    }

    /** [lane] waits out the pause after one more failure, or the server's `Retry-After` ([retryAfterMs]). */
    private fun held(lane: LanePause, retryAfterMs: Long?): Step {
        lane.afterFailure(retryAfterMs)
        return Step.Held
    }

    private suspend fun answered(event: QueuedEvent, code: Int, result: HttpResult.Failure): Step {
        val error = LegacyErrors.of(SdkError.Http(code), result.errorBody)
        return when {
            code in 500..599 || code in REPEATED_4XX -> {
                val attempts = event.attempts + 1
                if (attempts >= MAX_ATTEMPTS) {
                    drop(event, LogReasons.ATTEMPTS, error, attempts)
                    return Step.Next
                }
                // A disk that refuses the count keeps it in memory: the pause comes all the same.
                store.update(event.copy(attempts = attempts))
                held(laneOf(event), result.retryAfterMs.takeIf { code in RETRY_AFTER_CODES })
            }
            // A push event asks /init nothing: the shop's refusal reaches the app's own instance through its
            // own requests. 402 and 403 are the service's refusal, 404 an event the server does not take.
            code in SHOP_REFUSALS && event.lane == QueueLane.PUSH -> {
                drop(event, if (code == 404) LogReasons.REJECTED else LogReasons.SERVICE_REFUSED, error)
                Step.Next
            }
            code in SHOP_REFUSALS -> when {
                // The shop is fine, this service is not (an unpaid CRM for push/custom).
                event.shopChecked -> {
                    drop(event, LogReasons.SERVICE_REFUSED, error)
                    Step.Next
                }
                // From the background the check would send /init: it waits for the foreground.
                !lifecycle.isForeground -> {
                    checkDue = event.id to error
                    Step.Held
                }
                else -> checkShop(event, error)
            }
            else -> {
                drop(event, LogReasons.REJECTED, error)
                Step.Next
            }
        }
    }

    /**
     * The shop's check after a tracking [event] was answered `402`, `403` or `404` ([error]): `/init` asks
     * the server ([CoreInit.checkShopAndAwait]). Called in the foreground only.
     */
    private suspend fun checkShop(event: QueuedEvent, error: LegacyError): Step {
        val check = init.checkShopAndAwait(waitMs)
        // The session its /init started woke the queue: not a trigger that may end the pause below.
        urgent = false
        return when (check) {
            // A new answer found the shop fine: the event goes once more, after a pause if the disk failed.
            CoreInit.ShopCheck.Fine -> if (store.update(event.copy(shopChecked = true))) Step.Next else held(trackingLane, null)
            // Found fine less than 10 minutes ago: the service refuses, not the shop.
            CoreInit.ShopCheck.RecentlyFine -> {
                drop(event, LogReasons.SERVICE_REFUSED, error)
                Step.Next
            }
            // Cleared by the state's own news; a released queue sends nothing more.
            CoreInit.ShopCheck.Refused, CoreInit.ShopCheck.Released -> Step.Stop(Outcome.Idle)
            // The check may not go yet: the event waits for it, which no trigger ends. Or it told nothing: a pause.
            is CoreInit.ShopCheck.Unknown -> {
                val wait = check.retryInMs
                if (wait != null) {
                    trackingLane.pause(wait, serverNamed = true)
                    Step.Held
                } else {
                    held(trackingLane, null)
                }
            }
        }
    }

    /** The queue's pause after the disk failed to read it, or a pass failed unexpectedly. */
    private fun stall(): Outcome.Pause = Outcome.Pause(backoff.delayMs(stalls++, null))

    /** The event went: it leaves the queue and its listener hears the answer. */
    private fun delivered(event: QueuedEvent, body: Any, sentAt: Long): Step {
        laneOf(event).succeeded()
        stalls = 0
        // Gone meanwhile (cleared, or dropped for room): its listener heard that already. A file the disk
        // keeps is skipped from now on: the event is not sent again.
        val removed = store.remove(event.id) ?: return Step.Next
        val listener = removed.waiting?.listener ?: return Step.Next
        if (listener is EventAnswerListener) {
            listener.popupHeldBack = when {
                sentAt - event.createdAt > POPUP_WINDOW_MS -> LogReasons.LATE_EVENT
                !lifecycle.isForeground -> LogReasons.APP_IN_BACKGROUND
                else -> null
            }
        }
        delivery.success(listener, body)
        return Step.Next
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
        waiting?.let { delivery.failure(it.listener, error) }
    }

    private fun log(level: LogLevel, code: String, message: String, context: Map<String, Any?> = emptyMap(), throwable: Throwable? = null) =
        SdkLogs.log(level, code, message, shopId, context, throwable)

    internal companion object {
        const val PATH_PUSH = "push"
        const val PATH_CUSTOM = "push/custom"

        /** The push events (decision 0004): the server reads no `sid` in them, and none goes. */
        val PUSH_EVENT_PATHS: Set<String> = setOf("track/delivered", "track/opened", "track/clicked", "track/closed")

        /** What the queue takes. */
        val PATHS: Set<String> = setOf(PATH_PUSH, PATH_CUSTOM, "track/stories", "track/slider", "popup/showed") + PUSH_EVENT_PATHS

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
        private const val REASON_INVALID_JSON = "invalid_json"
        private val SHOP_REFUSALS = setOf(402, 403, 404)
        private val REPEATED_4XX = setOf(408, 410, 417, 426, 429)
        private val RETRY_AFTER_CODES = setOf(429, 503)

        private val EXPIRED = LegacyError(LegacyErrors.NO_ANSWER, "The event expired in the queue", SdkError.Timeout)
        private val OVERFLOWED = LegacyError(LegacyErrors.NO_ANSWER, "The event was dropped: the queue was full", SdkError.Cancelled)
        private val UNREADABLE = LegacyError(LegacyErrors.NO_ANSWER, "The event could not be read from the disk", SdkError.Decoding)
        private val CLEARED_REFUSED = LegacyError(LegacyErrors.NO_ANSWER, "The event was dropped: the shop refused the SDK", SdkError.Cancelled)
        private val NOT_KEPT = LegacyError(LegacyErrors.NO_ANSWER, "The event was not kept: the disk refused it and it cannot go from the background", SdkError.Cancelled)

        /** The one push event that moves the session's last activity: the server extends its session on it too. */
        private const val PATH_CLICKED = "track/clicked"

        /** Whether an event of [path] moves the session's last activity (core/session.md). */
        fun extendsSession(path: String): Boolean = path !in PUSH_EVENT_PATHS || path == PATH_CLICKED
    }
}
