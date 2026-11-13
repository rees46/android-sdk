package com.personalization.core.internal

import com.personalization.Cancellable
import com.personalization.LogLevel
import com.personalization.core.SdkState
import com.personalization.logs.LogCodes
import com.personalization.logs.SdkLogs
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The [SdkState] of one instance and the app's listeners of it, for both cores: the legacy path moves
 * it from `RegisterManager`, the new core from [CoreInit].
 *
 * A new listener hears the current state at once, then each change once, on the main thread, in the
 * order the changes happened. A listener that throws is logged as `callback_failed` and keeps its
 * later changes; a cancelled one gets nothing more.
 */
internal class InstanceState(
    private val shopId: () -> String?,
    private val dispatchers: CoreDispatchers = CoreDispatchers.DEFAULT,
) {

    private val lock = Any()
    private val flow = MutableStateFlow(SdkState.NOT_INITIALIZED)
    private val listeners = CopyOnWriteArrayList<Subscription>()

    /** Created with the first listener: a process without one never loads the main dispatcher. */
    private val delivery: CoroutineScope by lazy {
        CoroutineScope(SupervisorJob() + dispatchers.main + CoroutineExceptionHandler { _, throwable ->
            SdkLogs.log(LogLevel.ERROR, LogCodes.CALLBACK_FAILED, "A state listener could not be called", shopId(), throwable = throwable)
        })
    }

    val current: SdkState get() = flow.value

    /** The state as a flow, for the core's own waits. */
    val changes: StateFlow<SdkState> get() = flow.asStateFlow()

    /** Moves to [state]; false when the instance is already there, and then nobody hears of it. */
    fun set(state: SdkState): Boolean = synchronized(lock) {
        if (flow.value == state) return false
        flow.value = state
        SdkLogs.log(LogLevel.DEBUG, LogCodes.SDK_LOG, "State: $state", shopId(), mapOf("state" to state.name.lowercase()))
        // Scheduled under the lock: two changes from two threads reach the main thread in their order.
        val targets = listeners.toList()
        if (targets.isNotEmpty()) delivery.launch { targets.forEach { it.deliver(state) } }
        true
    }

    /**
     * Calls [listener] on the main thread with the current state at once, then with every later change
     * until the handle is cancelled. The current state is scheduled under the lock a change takes, so
     * a change made meanwhile arrives after it, never before.
     */
    fun addListener(listener: (SdkState) -> Unit): Cancellable {
        val subscription = Subscription(listener)
        synchronized(lock) {
            listeners += subscription
            val current = flow.value
            delivery.launch { subscription.deliver(current) }
        }
        return Cancellable {
            subscription.active = false
            listeners -= subscription
        }
    }

    /** Forgets every listener; a change already on its way to them still arrives. */
    fun dropListeners() = listeners.clear()

    private inner class Subscription(private val listener: (SdkState) -> Unit) {
        @Volatile
        var active = true

        fun deliver(state: SdkState) {
            if (!active) return
            try {
                listener(state)
            } catch (e: Exception) {
                SdkLogs.log(LogLevel.ERROR, LogCodes.CALLBACK_FAILED, "The app's state listener threw", shopId(), throwable = e)
            }
        }
    }
}
