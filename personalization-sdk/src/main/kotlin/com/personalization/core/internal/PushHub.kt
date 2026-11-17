package com.personalization.core.internal

import android.os.Handler
import android.os.Looper
import com.personalization.Cancellable
import com.personalization.LogLevel
import com.personalization.PushProvider
import com.personalization.core.BridgeApi
import com.personalization.core.BridgePushListener
import com.personalization.core.PushCallbacks
import com.personalization.core.PushEvent
import com.personalization.core.PushListener
import com.personalization.core.PushPayload
import com.personalization.core.PushToken
import com.personalization.core.PushTokenListener
import com.personalization.logs.LogCodes
import com.personalization.logs.SdkLogs
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor

/**
 * What the process knows of pushes beside the instances: the facade's [PushCallbacks], the bridges'
 * listeners, which events of which messages went already, and which tokens were reported.
 *
 * Callbacks run on the main thread ([main]); one that throws is logged as `callback_failed`.
 */
@OptIn(BridgeApi::class)
internal object PushHub {

    /** The facade's callbacks (`Rees46.setPushCallbacks`). */
    @Volatile
    var callbacks: PushCallbacks? = null

    private val eventSinks = CopyOnWriteArrayList<BridgePushListener>()
    private val tokenSinks = CopyOnWriteArrayList<PushTokenListener>()

    /** Where callbacks run: the main thread. */
    @Volatile
    internal var main: Executor = MainThread

    private object MainThread : Executor {
        private val handler by lazy { Handler(Looper.getMainLooper()) }
        override fun execute(command: Runnable) {
            handler.post(command)
        }
    }

    /**
     * The events of the messages a queue accepted in this process, as `shop|event|code`: one event of one
     * message goes once. Bounded: the oldest are forgotten past [SEEN_LIMIT]. Guards [reserved] too.
     */
    private val seen = object : LinkedHashMap<String, Boolean>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean = size > SEEN_LIMIT
    }

    /**
     * The events being handed to a queue now ([reserve]), with the repeats that came meanwhile: they try
     * again if that one is not accepted ([release]).
     */
    private val reserved = HashMap<String, MutableList<() -> Unit>>()

    /**
     * What the repeats parked beside [reserved] hear when the first one is accepted ([confirm]): the
     * `notifications` methods, whose caller waits for a result, end there. Guarded like [reserved].
     */
    private val parkedRepeats = HashMap<String, MutableList<() -> Unit>>()

    /** What [reserve] decided for one event of one message. */
    enum class Reservation {
        /** Taken: the caller hands the event to its queue, then [confirm]s or [release]s it. */
        TAKEN,

        /** A queue accepted it already: a repeat, nothing goes. */
        REPEAT,

        /** Being handed to a queue right now: this one tries again only if that one is not accepted. */
        PARKED,
    }

    /**
     * The pushes handed to a shop in this process, as they came and with the shop they went to, by
     * `type|code`: a notification the SDK draws reports them whole ([received]). Bounded like [seen].
     */
    private val pushes = object : LinkedHashMap<String, Map<String, String>>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Map<String, String>>?): Boolean = size > PUSHES_LIMIT
    }

    /** The last token of each provider the facade's `onToken` heard of. */
    private val facadeReported = ConcurrentHashMap<PushProvider, String>()

    /** The last token of each provider the SDK got in this process: what the bridges heard, or hear on joining. */
    private val sinkReported = ConcurrentHashMap<PushProvider, String>()

    /**
     * Whether [event] of the message [code] may go to [shopId]'s queue now: once per process. The event
     * counts as sent only once a queue accepted it ([confirm]); one the queue did not take is [release]d
     * and may go when it comes again. A repeat that comes while the first is being handed over is
     * [Reservation.PARKED]: [retry] runs if the first is released, so the two are never lost together, and
     * [repeat] if the first is accepted, as a repeat.
     */
    fun reserve(shopId: String, event: PushEvent, code: String, repeat: () -> Unit = {}, retry: () -> Unit): Reservation = synchronized(seen) {
        val key = key(shopId, event, code)
        if (seen.containsKey(key)) return Reservation.REPEAT
        val waiting = reserved[key]
        if (waiting != null) {
            waiting += retry
            parkedRepeats.getOrPut(key) { ArrayList() } += repeat
            return Reservation.PARKED
        }
        reserved[key] = ArrayList()
        Reservation.TAKEN
    }

    /**
     * A queue accepted the event [reserve] took: a repeat sends nothing from now on, the parked ones included,
     * which hear their `repeat` now, on this thread.
     */
    fun confirm(shopId: String, event: PushEvent, code: String) {
        val key = key(shopId, event, code)
        val repeats = synchronized(seen) {
            reserved.remove(key)
            seen[key] = true
            parkedRepeats.remove(key)
        }.orEmpty()
        repeats.forEach { repeat ->
            try {
                repeat()
            } catch (e: Exception) {
                SdkLogs.log(LogLevel.ERROR, LogCodes.PUSH_FAILED, "A repeat of a push event could not be told it was sent", shopId, throwable = e)
            }
        }
    }

    /**
     * The queue did not take the event [reserve] took: it may go when it comes again, and the repeats
     * parked meanwhile try again now, on this thread.
     */
    fun release(shopId: String, event: PushEvent, code: String) {
        val parked = synchronized(seen) {
            // The repeats try again, and each tells its own end.
            parkedRepeats.remove(key(shopId, event, code))
            reserved.remove(key(shopId, event, code))
        }.orEmpty()
        parked.forEach { retry ->
            try {
                retry()
            } catch (e: Exception) {
                SdkLogs.log(LogLevel.ERROR, LogCodes.PUSH_FAILED, "A repeat of a push event could not be tried again", shopId, throwable = e)
            }
        }
    }

    private fun key(shopId: String, event: PushEvent, code: String) = "$shopId|${event.name}|$code"

    /** Keeps [payload], handed to its shop ([PushPayload.shopId]), for [received]. */
    fun remember(payload: PushPayload) {
        val shopId = payload.shopId ?: return
        val push = if (payload.data[PushPayload.SHOP_ID].isNullOrEmpty()) payload.data + (PushPayload.SHOP_ID to shopId) else payload.data
        synchronized(pushes) { pushes["${payload.type}|${payload.code}"] = push }
    }

    /** The push of mailing [type] and message [code] as it came in this process, with its `shop_id`; null when it did not. */
    fun received(code: String, type: String): Map<String, String>? = synchronized(pushes) { pushes["$type|$code"] }

    /**
     * Tells the callbacks about [event]: the shop's own one for it ([own]) when set, otherwise the
     * facade's; and every bridge listener.
     */
    fun event(event: PushEvent, payload: PushPayload, own: PushCallbacks?) {
        val listener = listenerOf(own, event) ?: listenerOf(callbacks, event)
        val sinks = eventSinks.toList()
        if (listener == null && sinks.isEmpty()) return
        run(payload.shopId) {
            listener?.let { call(payload.shopId, "A push callback threw") { it.onPush(payload) } }
            sinks.forEach { sink -> call(payload.shopId, "A bridge's push listener threw") { sink.onPushEvent(event, payload) } }
        }
    }

    /**
     * Tells the callbacks about a token an instance got from its provider: the instance's own `onToken`
     * ([own], which keeps track of what it heard through [ownReported]) or, without one, the facade's;
     * the facade and the bridges hear each token once per process.
     */
    fun token(token: PushToken, shopId: String?, own: PushCallbacks?, ownReported: MutableMap<PushProvider, String>) {
        val ownListener = own?.onToken
        val ownNew = ownListener != null && ownReported.put(token.provider, token.token) != token.token
        val facadeListener = if (ownListener == null) callbacks?.onToken else null
        val facade = facadeListener?.takeIf { facadeReported.put(token.provider, token.token) != token.token }
        // With the sinks' lock: a sink that joins meanwhile hears this token once, here or on joining.
        val sinks = synchronized(tokenSinks) {
            if (sinkReported.put(token.provider, token.token) != token.token) tokenSinks.toList() else emptyList()
        }
        if (!ownNew && facade == null && sinks.isEmpty()) return
        run(shopId) {
            if (ownNew) ownListener?.let { call(shopId, "A push token callback threw") { it.onToken(token) } }
            facade?.let { call(shopId, "A push token callback threw") { it.onToken(token) } }
            sinks.forEach { sink -> call(shopId, "A bridge's token listener threw") { sink.onToken(token) } }
        }
    }

    fun addEventSink(sink: BridgePushListener): Cancellable {
        eventSinks += sink
        return Cancellable { eventSinks -= sink }
    }

    /**
     * Adds a bridge's token listener. A token the SDK got before it joined comes to it at once, once per
     * provider, on the main thread: a bridge that registers after the token came still hears it.
     */
    fun addTokenSink(sink: PushTokenListener): Cancellable {
        val current = synchronized(tokenSinks) {
            tokenSinks += sink
            sinkReported.map { (provider, token) -> PushToken(token, provider) }
        }
        if (current.isNotEmpty()) {
            run(null) { current.forEach { token -> call(null, "A bridge's token listener threw") { sink.onToken(token) } } }
        }
        return Cancellable { tokenSinks -= sink }
    }

    private fun listenerOf(callbacks: PushCallbacks?, event: PushEvent): PushListener? = when (event) {
        PushEvent.DELIVERED -> callbacks?.onDelivered
        PushEvent.OPENED -> callbacks?.onOpened
        PushEvent.CLICKED -> callbacks?.onClicked
        PushEvent.CLOSED -> callbacks?.onClosed
    }

    private fun run(shopId: String?, block: () -> Unit) {
        try {
            main.execute(block)
        } catch (e: Exception) {
            SdkLogs.log(LogLevel.ERROR, LogCodes.CALLBACK_FAILED, "A push callback could not be scheduled", shopId, throwable = e)
        }
    }

    private inline fun call(shopId: String?, message: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            SdkLogs.log(LogLevel.ERROR, LogCodes.CALLBACK_FAILED, message, shopId, throwable = e)
        }
    }

    /** Test-only: forgets everything. */
    internal fun reset() {
        callbacks = null
        eventSinks.clear()
        tokenSinks.clear()
        synchronized(seen) {
            seen.clear()
            reserved.clear()
            parkedRepeats.clear()
        }
        forgetPushes()
        facadeReported.clear()
        sinkReported.clear()
        main = MainThread
    }

    /** Test-only: forgets the pushes that came, as a new process would. */
    internal fun forgetPushes() = synchronized(pushes) { pushes.clear() }

    private const val SEEN_LIMIT = 1_000
    private const val PUSHES_LIMIT = 100
}
