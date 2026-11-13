package com.personalization.core.internal

import android.os.Handler
import android.os.Looper
import com.personalization.LogLevel
import com.personalization.PushProvider
import com.personalization.core.PushCallbacks
import com.personalization.core.PushEvent
import com.personalization.core.PushListener
import com.personalization.core.PushPayload
import com.personalization.core.PushToken
import com.personalization.logs.LogCodes
import com.personalization.logs.SdkLogs
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor

/**
 * What the process knows of pushes beside the instances: the facade's [PushCallbacks], which events of
 * which messages went already, and which tokens were reported.
 *
 * Callbacks run on the main thread ([main]); one that throws is logged as `callback_failed`.
 */
internal object PushHub {

    /** The facade's callbacks (`Rees46.setPushCallbacks`). */
    @Volatile
    var callbacks: PushCallbacks? = null

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

    /** The last token of each provider the facade's `onToken` heard of. */
    private val facadeReported = ConcurrentHashMap<PushProvider, String>()

    /** True the first time [event] of the message [code] comes for [shopId] in this process. */
    fun firstTime(shopId: String, event: PushEvent, code: String): Boolean = synchronized(seen) {
        seen.put("$shopId|${event.name}|$code", true) == null
    }

    /**
     * Tells the callbacks about [event]: the shop's own one for it ([own]) when set, otherwise the
     * facade's.
     */
    fun event(event: PushEvent, payload: PushPayload, own: PushCallbacks?) {
        val listener = listenerOf(own, event) ?: listenerOf(callbacks, event) ?: return
        run(payload.shopId) { call(payload.shopId, "A push callback threw") { listener.onPush(payload) } }
    }

    /**
     * Tells the callbacks about a token an instance got from its provider: the instance's own `onToken`
     * ([own], which keeps track of what it heard through [ownReported]) or, without one, the facade's;
     * the facade hears each token once per process.
     */
    fun token(token: PushToken, shopId: String?, own: PushCallbacks?, ownReported: MutableMap<PushProvider, String>) {
        val ownListener = own?.onToken
        val ownNew = ownListener != null && ownReported.put(token.provider, token.token) != token.token
        val facadeListener = if (ownListener == null) callbacks?.onToken else null
        val facade = facadeListener?.takeIf { facadeReported.put(token.provider, token.token) != token.token }
        if (!ownNew && facade == null) return
        run(shopId) {
            if (ownNew) ownListener?.let { call(shopId, "A push token callback threw") { it.onToken(token) } }
            facade?.let { call(shopId, "A push token callback threw") { it.onToken(token) } }
        }
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
        synchronized(seen) { seen.clear() }
        facadeReported.clear()
        main = MainThread
    }

    private const val SEEN_LIMIT = 1_000
}
