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
 * `track/slider`, `popup/showed` and the push events (`track/delivered`, `track/opened`, `track/clicked`,
 * `track/closed`) go through it, in order, and survive no network and a restart.
 *
 * - **Accepting.** [accept] answers a released instance with `notInitialized`, a refused shop with
 *   `shopBlocked` and fields JSON cannot hold with `invalidArgument`, at once; otherwise it moves the
 *   session's last activity and hands the event to a writer that keeps the calls' order: an event is
 *   accepted once its file is on disk ([QueueStore]), and the caller's `accepted` hears it then. A disk
 *   that refuses it sends it once, as a request.
 * - **Stamping.** The common fields — `did`, the `sid` of the moment, `segment`, `stream` — go on a copy
 *   when the event is sent ([stamp]), with the stored tracking source as a `source` object on `/push` only
 *   and the time it was made as `time` on `/push/custom` only (the only path that reads it), unless the
 *   call set its own. Numbers the older builders put in as text go as numbers ([EventNumbers]). Nothing
 *   stamped reaches the disk.
 * - **Sending.** One event at a time, oldest first, once the instance is ready and the session alive
 *   (a session whose window ended gets its `/init` first), by the one process of the app that holds the
 *   queue's file lock. An event cleared while it waited (the customer's deletion) does not go. Sending
 *   starts on [SdkState.READY], on the app's foreground and background, on the network's return, on
 *   [flush] and on a timer.
 * - **Answers.** `2xx` removes the event. No network waits for it and a timeout repeats it after a pause,
 *   neither counting as an attempt. `5xx`, `408`, `410`, `417`, `426`, `429` repeat it after a pause
 *   (the `Retry-After` of a `429` or `503`), and the 10th drops it. `402`, `403`, `404` pause the queue
 *   while `/init` checks the shop ([CoreInit.checkShopAndAwait]): a refusal clears the queue, a shop
 *   found fine by a new answer sends the event once more, and a second such answer drops it; a check
 *   that may not go yet holds the event until it may. Other answers drop it at once. A dropped event
 *   does not hold the others. A disk that fails to read or rewrite an event pauses the queue instead of
 *   sending it again at once.
 * - **Limits.** An event older than 7 days is dropped when its turn comes; at 1000 events the oldest go.
 *   Each drop is `queue_dropped` with its reason.
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

    /** Failures in a row since the last event went: the index of the next pause. The worker's own. */
    private var failures = 0

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
    }

    private sealed interface Outcome {
        /** Nothing to send, or not this queue's to send: wait for a trigger. */
        object Idle : Outcome

        /** The instance or its session is not there yet, or another process sends: wait for a trigger or the timer. */
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
        init.session.extend(createdAt)
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
     * check or a server's `Retry-After`. A pause of the backoff ends: the event waiting in it goes again at
     * once.
     *
     * @throws SdkException `notInitialized` after [release], `shopBlocked` for a refused shop.
     */
    suspend fun flush() {
        if (released) throw SdkException(SdkError.NotInitialized)
        if (state.current == SdkState.REFUSED) throw SdkException(SdkError.ShopBlocked)
        val done = CompletableDeferred<Unit>()
        if (writer?.isActive != true || commands.trySend(Command.Flush(done)).isFailure) throw SdkException(SdkError.NotInitialized)
        done.await()
    }

    /**
     * Empties the queue for [reason] and runs [then] while no event can be accepted: an event accepted
     * before this call goes, one accepted after it stays. The listeners waiting hear [error]. Returns
     * [then]'s result. Without a writer to keep the order (released) it is done here.
     */
    suspend fun clear(reason: String, error: LegacyError, then: () -> Boolean): Boolean {
        val done = CompletableDeferred<Boolean>()
        if (released || writer?.isActive != true || commands.trySend(Command.Clear(reason, error, then, done)).isFailure) {
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
     * and an answer that says it went takes the event off the disk. Without, as when a push-only instance
     * gives way to the app's, the event in flight finishes and the listeners stay for the queue that
     * sends next. Events accepted before are still written; a flush asked before it and not
     * handled yet ends with `notInitialized`.
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
            delivery.failure(accept.listener, LegacyErrors.of(SdkError.Cancelled))
        }
        accept.accepted?.let { tell(it, null) }
        if (event == null) {
            // Not accepted: the disk refused it (storage_failed). It goes once, as a request, with what
            // the queue would have put on it.
            sendDirectly(accept.path, withOwnFields(accept.path, JSONObject(accept.body.toString()), accept.createdAt), accept.listener)
            return
        }
        // The queue that sends: this one, or the one made after it.
        (store.owner ?: this).kick(urgent = false)
    }

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
            val outcome = try {
                val tried = synchronized(flushLock) { flushes.toList().also { flushes.clear() } }
                val outcome = if (store.isOwner(this)) store.sending.withLock { passHoldingLock() } else Outcome.Idle
                tried.forEach { it.complete(Unit) }
                outcome
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Never a tight loop: the next pass comes after a pause.
                log(LogLevel.ERROR, LogCodes.SDK_LOG, "Sending the event queue failed", throwable = e)
                pause(null)
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

    /** One pass while this process holds the queue's file lock; when another process holds it, that one sends. */
    private suspend fun passHoldingLock(): Outcome {
        if (!store.lockForSending()) return Outcome.Waiting
        try {
            return pass()
        } finally {
            store.unlockForSending()
        }
    }

    /** Sends events oldest first until the queue is empty or cannot send now. */
    private suspend fun pass(): Outcome {
        urgent = false
        while (true) {
            if (released || !store.isOwner(this) || state.current == SdkState.REFUSED) return Outcome.Idle
            // A clear gives the lock back: it is taken again for what came after it.
            if (!store.lockForSending()) return Outcome.Waiting
            val generation = store.generation
            val unreadable = ArrayList<QueueStore.Waiting>()
            val head = store.head(unreadable)
            unreadable.forEach { delivery.failure(it.listener, UNREADABLE) }
            val event = when (head) {
                QueueStore.Head.Empty -> return Outcome.Idle
                // The disk failed to read it: later, not again at once.
                QueueStore.Head.Unavailable -> return pause(null)
                is QueueStore.Head.Event -> head.event
            }
            if (clock.nowMillis() - event.createdAt > TTL_MS) {
                drop(event, LogReasons.EXPIRED, EXPIRED)
                continue
            }
            if (!connectivity.isConnected) return Outcome.Offline
            // Ready, and when the session's window ended, the /init of a new one first.
            if (init.awaitUsable(waitMs) != null || !init.session.isAlive()) return Outcome.Waiting
            // Cleared while it waited (the customer was deleted): it does not go under the next one.
            val body = store.ifCurrent(generation, event.id) { stamped(event) } ?: continue
            val sentAt = clock.nowMillis()
            val result = sendOne(event, body)
            // The answer is in: what it decides is done even if the queue is released meanwhile.
            val next = withContext(NonCancellable) { take(event, result, sentAt) }
            if (next != null) return next
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
     * end, and an answer that says it went still takes the event off the disk, so it does not go twice.
     */
    private suspend fun sendOne(event: QueuedEvent, body: JSONObject): HttpResult {
        val call = writing.async { request(event, body) }
        try {
            return call.await()
        } catch (e: CancellationException) {
            writing.launch {
                val result = call.await()
                if (result is HttpResult.Success || (result as? HttpResult.Failure)?.error == SdkError.Decoding) store.remove(event.id)
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
                // A disk that refuses the count keeps it in memory: the pause comes all the same.
                store.update(event.copy(attempts = attempts))
                return pause(result.retryAfterMs.takeIf { code in RETRY_AFTER_CODES })
            }
            code in SHOP_REFUSALS -> {
                if (event.shopChecked) {
                    // The shop is fine, this service is not (an unpaid CRM for push/custom).
                    drop(event, LogReasons.SERVICE_REFUSED, error)
                    return null
                }
                val check = init.checkShopAndAwait(waitMs)
                // The session its /init started woke the queue: not a trigger that may end the pause below.
                urgent = false
                return when (check) {
                    // A new answer found the shop fine: the event goes once more, after a pause if the disk failed.
                    CoreInit.ShopCheck.Fine -> if (store.update(event.copy(shopChecked = true))) null else pause(null)
                    // Found fine less than 10 minutes ago: the service refuses, not the shop.
                    CoreInit.ShopCheck.RecentlyFine -> {
                        drop(event, LogReasons.SERVICE_REFUSED, error)
                        null
                    }
                    // Cleared by the state's own news; a released queue sends nothing more.
                    CoreInit.ShopCheck.Refused, CoreInit.ShopCheck.Released -> Outcome.Idle
                    // The check may not go yet: the event waits for it. Or it told nothing: a pause.
                    is CoreInit.ShopCheck.Unknown -> check.retryInMs?.let { Outcome.Pause(it, serverNamed = true) } ?: pause(null)
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
        // Gone meanwhile (cleared, or dropped for room): its listener heard that already. A file the disk
        // keeps is skipped from now on: the event is not sent again.
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
    }
}
