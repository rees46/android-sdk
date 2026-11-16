package com.personalization.core.internal

import com.personalization.Cancellable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import java.util.concurrent.CopyOnWriteArrayList

/**
 * What a request with a result waits for, as [CoreV2] waits: [CoreInit.usable] within [timeoutMs], `timeout`
 * when the wait runs out.
 */
internal suspend fun CoreInit.usableWithin(timeoutMs: Long): com.personalization.core.SdkError? {
    var verdict: com.personalization.core.SdkError? = com.personalization.core.SdkError.Timeout
    kotlinx.coroutines.withTimeoutOrNull(timeoutMs) { verdict = usable() }
    return verdict
}

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

    /** A change nobody hears of, as when the system's callback does not come. */
    fun setQuietly(connected: Boolean) {
        isConnected = connected
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

/**
 * The main thread for a test that runs on it (Robolectric's): what the SDK hands to "main" waits here
 * until the test runs it, on its own thread, with [runNext] — a wait on the work itself, not on the clock.
 */
internal class HeldMain : kotlinx.coroutines.CoroutineDispatcher() {
    private val tasks = java.util.concurrent.LinkedBlockingQueue<Runnable>()

    override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) {
        tasks.put(block)
    }

    /** Runs what was handed over, waiting up to [timeoutMs] for the first; false when nothing came. */
    fun runNext(timeoutMs: Long = 5_000): Boolean {
        val first = tasks.poll(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS) ?: return false
        first.run()
        while (true) (tasks.poll() ?: break).run()
        return true
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

/**
 * A started [CoreInit] for [shopId], whose `/init` [send] answers without a server: with the `did`
 * [settings] already hold, the instance is ready at once. For tests of what goes after `/init`.
 */
internal fun startedCoreInit(
    context: android.content.Context,
    settings: com.personalization.sdk.domain.repositories.UserSettingsRepository,
    connectivity: ConnectivityMonitor = FakeConnectivity(connected = true),
    shopId: String = "shop",
    dispatchers: CoreDispatchers = CoreDispatchers.DEFAULT,
    send: suspend (org.json.JSONObject) -> HttpResult = {
        HttpResult.Success(org.json.JSONObject().put("did", settings.getDid().ifEmpty { "did" }).put("seance", "S"))
    },
): CoreInit = CoreInit(
    setup = InitSetup(shopId),
    send = send,
    settings = settings,
    storage = ShopStorage(context, StorageLayout(shopId, null)),
    state = InstanceState(shopId = { shopId }, dispatchers = dispatchers),
    connectivity = connectivity,
    lifecycle = FakeAppLifecycle(foreground = true),
    hooks = object : InitHooks {
        override fun onAnswer(answer: org.json.JSONObject, fresh: Boolean) = Unit
        override fun onUsable() = Unit
    },
    dispatchers = dispatchers,
).also { it.start() }
