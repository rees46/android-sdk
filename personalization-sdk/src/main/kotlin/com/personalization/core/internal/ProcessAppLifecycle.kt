package com.personalization.core.internal

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.personalization.Cancellable

/**
 * The app's foreground as `ProcessLifecycleOwner` sees it: started while an activity of the app is,
 * stopped a moment after the last one stops (a rotation is not a trip to the background).
 *
 * A listener hears changes only. The lifecycle replays its current state to a new observer; that replay
 * is no change and is not passed on, so an instance made in the foreground does not hear "foreground".
 * Observers are added and removed on the main thread, as the lifecycle requires.
 */
internal class ProcessAppLifecycle(
    private val owner: () -> LifecycleOwner = { ProcessLifecycleOwner.get() },
) : AppLifecycle {

    private val main = Handler(Looper.getMainLooper())

    override val isForeground: Boolean
        get() = owner().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

    override fun addListener(listener: (foreground: Boolean) -> Unit): Cancellable {
        val subscription = Subscription(listener)
        onMain { subscription.attach() }
        return Cancellable { onMain { subscription.detach() } }
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    /** Lives on the main thread. */
    private inner class Subscription(private val listener: (Boolean) -> Unit) : LifecycleEventObserver {
        private var lifecycle: Lifecycle? = null
        private var detached = false
        private var last = false

        fun attach() {
            if (detached || lifecycle != null) return
            val target = owner().lifecycle
            last = target.currentState.isAtLeast(Lifecycle.State.STARTED)
            lifecycle = target
            target.addObserver(this)
        }

        fun detach() {
            detached = true
            lifecycle?.removeObserver(this)
            lifecycle = null
        }

        override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
            val foreground = when (event) {
                Lifecycle.Event.ON_START -> true
                Lifecycle.Event.ON_STOP -> false
                else -> return
            }
            if (detached || foreground == last) return
            last = foreground
            listener(foreground)
        }
    }
}
