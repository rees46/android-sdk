package com.personalization.core.internal

import com.personalization.Cancellable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import java.util.concurrent.CopyOnWriteArrayList

/** A clock that moves only when the test says so. */
internal class FakeClock(var now: Long = 1_700_000_000_000L) : Clock {
    override fun nowMillis(): Long = now

    fun advance(millis: Long) {
        now += millis
    }
}

/** Network on or off, switched by the test; listeners hear each switch. */
internal class FakeConnectivity(connected: Boolean = true) : ConnectivityMonitor {
    private val listeners = CopyOnWriteArrayList<(Boolean) -> Unit>()

    override var isConnected: Boolean = connected
        private set

    override fun addListener(listener: (connected: Boolean) -> Unit): Cancellable {
        listeners += listener
        return Cancellable { listeners -= listener }
    }

    fun set(connected: Boolean) {
        if (connected == isConnected) return
        isConnected = connected
        listeners.forEach { it(connected) }
    }
}

/** Foreground or background, switched by the test; listeners hear each switch. */
internal class FakeAppLifecycle(foreground: Boolean = true) : AppLifecycle {
    private val listeners = CopyOnWriteArrayList<(Boolean) -> Unit>()

    override var isForeground: Boolean = foreground
        private set

    override fun addListener(listener: (foreground: Boolean) -> Unit): Cancellable {
        listeners += listener
        return Cancellable { listeners -= listener }
    }

    fun set(foreground: Boolean) {
        if (foreground == isForeground) return
        isForeground = foreground
        listeners.forEach { it(foreground) }
    }
}

/** Every dispatcher of the core on one test scheduler: the test decides when work runs. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun testCoreDispatchers(scheduler: TestCoroutineScheduler = TestCoroutineScheduler()): CoreDispatchers {
    val dispatcher = StandardTestDispatcher(scheduler)
    return CoreDispatchers(io = dispatcher, main = { dispatcher }, default = dispatcher)
}

/**
 * Threads for the legacy path ([LegacyCore.threads]) that a test can wait on: [awaitIdle] returns once
 * every request started so far has had its answer delivered, the requests those answers started too.
 */
internal class TrackingThreads : java.util.concurrent.Executor {
    private val lock = Object()
    private var running = 0

    override fun execute(command: Runnable) {
        synchronized(lock) { running++ }
        Thread {
            try {
                command.run()
            } finally {
                synchronized(lock) {
                    running--
                    lock.notifyAll()
                }
            }
        }.start()
    }

    fun awaitIdle(timeoutMs: Long = 10_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        synchronized(lock) {
            while (running > 0) {
                val left = deadline - System.currentTimeMillis()
                check(left > 0) { "$running requests of the legacy path still running" }
                lock.wait(left)
            }
        }
    }
}
