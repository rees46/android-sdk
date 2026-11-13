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
     * The events of the messages handled in this process, as `shop|event|code`: one event of one message
     * goes once. Bounded: the oldest are forgotten past [SEEN_LIMIT].
     */
    private val seen = object : LinkedHashMap<String, Boolean>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean = size > SEEN_LIMIT
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
     * True the first time [event] of the message [code] comes for [shopId] in this process: it is taken,
     * and a repeat is not sent. [forget] gives it back when the queue did not accept the event.
     */
    fun firstTime(shopId: String, event: PushEvent, code: String): Boolean = synchronized(seen) {
        seen.put("$shopId|${event.name}|$code", true) == null
    }

    /** [event] of the message [code] was not accepted for [shopId]: it may come again and go. */
    fun forget(shopId: String, event: PushEvent, code: String) {
        synchronized(seen) { seen.remove("$shopId|${event.name}|$code") }
    }

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
        synchronized(seen) { seen.clear() }
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
