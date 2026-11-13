package com.personalization.core.internal

import com.personalization.Cancellable
import com.personalization.LogLevel
import com.personalization.core.SdkError
import com.personalization.core.SdkState
import com.personalization.logs.LogCodes
import com.personalization.logs.LogReasons
import com.personalization.logs.SdkLogs
import com.personalization.sdk.data.models.params.UserBasicParams
import com.personalization.sdk.domain.repositories.UserSettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.TimeZone

/** What `/init` takes from the instance's config. */
internal class InitSetup(
    val shopId: String,
    /** Forget the `did` before `/init`, so the server hands out a new one. */
    val needReInitialization: Boolean = false,
    /** A `did` the app brings from another SDK; taken only while the shop has none. */
    val deviceId: String? = null,
    /** Reads the advertising id; null while `sendAdvertisingId` is off, and then it is never read. */
    val advertisingId: (suspend () -> String?)? = null,
    /** Extra query fields of `/init` (the test mode of popups v3). */
    val extraParams: () -> Map<String, String> = { emptyMap() },
    /** True on a Google Play pre-launch report device, where `/init` is not sent. */
    val skipInit: () -> Boolean = { false },
    /**
     * Brought up only to send a push's event (`Rees46.handlePush` for a shop registered but not created):
     * `/init` never goes. The instance is ready on the stored `did`, and a new session, a check of the shop
     * or the foreground ask nothing of the server: the app's own instance of the shop does.
     */
    val pushOnly: Boolean = false,
)

/** What the instance does with `/init`. Called on a background thread; an exception is logged. */
internal interface InitHooks {
    /**
     * An answer to act on: [fresh] from the server now (its popup is shown), or the saved one while
     * `/init` is repeated (its flags only: a popup is never shown twice).
     */
    fun onAnswer(answer: JSONObject, fresh: Boolean)

    /** The instance can send from now on: once per start, and again after a restart. */
    fun onUsable()
}

/**
 * The new core's `/init` (`core/init.md`, decision 0002) and the [SdkState] it gives the instance.
 *
 * - **Start.** A shop refused at an earlier launch starts [SdkState.REFUSED]. A shop with a `did` —
 *   stored, brought in [InitSetup.deviceId], or from the saved answer — is [SdkState.READY] at once and
 *   `/init` goes in the background (decision 1). Otherwise it is [SdkState.INITIALIZING].
 * - **Answers.** A `2xx` with a `did` is [SdkState.READY]: the `did` is kept if the shop had none, the
 *   `seance` becomes the `sid`, the answer is saved and the refused state cleared. `402`, `403`, `404`
 *   and other `4xx` are [SdkState.REFUSED], kept on disk. No answer, a `2xx` without a `did`, `5xx`,
 *   `408`, `410`, `417`, `426` and `429` are [SdkState.RETRYING]: `/init` is repeated after [Backoff]'s
 *   pause, or the `Retry-After` of a `429` or `503`. A shop refused at an earlier launch stays refused
 *   until the server answers.
 * - **Without an answer** an instance with a `did` works as [SdkState.READY] (`init_saved_answer`); one
 *   without waits for the network instead of failing again.
 * - **Later `/init`.** When the session's window ended ([SessionWindow], `core/session.md`): found by
 *   a request ([awaitUsable]), by the queue before a tracking event in the foreground ([renewSession]) or on coming back to the
 *   foreground; and on [checkShop]. A refused shop is not asked again before the next launch.
 * - **Session.** `/init` carries the `sid` only while its window is alive; the `seance` of the answer is
 *   the session from then on. The core never makes a `sid` of its own.
 * - **Push only** ([InitSetup.pushOnly]). `/init` never goes: the instance is ready on the stored `did`,
 *   and neither a new session, a check of the shop nor the foreground sends one.
 *
 * Requests with a result wait in [awaitUsable]: for the instance to be ready and, when the window
 * ended, for the `/init` of a new session. A stale answer (an `/init` of a start [restart] or [release]
 * ended) is dropped, and while [holdForRestart] holds the instance no `/init` goes at all.
 *
 * Disk work stays off the caller's thread: [start] decides the first state from what the preferences
 * already hold (a stored `did` is ready at once) and prepares the rest of the storage on [CoreDispatchers.io]
 * before the first `/init`. The app's foreground is handled there too, so the main thread never waits
 * for an answer being written under the lock.
 */
internal class CoreInit(
    private val setup: InitSetup,
    private val send: suspend (params: JSONObject) -> HttpResult,
    private val settings: UserSettingsRepository,
    private val storage: ShopStorage,
    private val state: InstanceState,
    private val connectivity: ConnectivityMonitor,
    private val lifecycle: AppLifecycle,
    private val hooks: InitHooks,
    dispatchers: CoreDispatchers = CoreDispatchers.DEFAULT,
    private val clock: Clock = Clock.SYSTEM,
    private val backoff: Backoff = Backoff(),
    private val timeZone: () -> TimeZone = { TimeZone.getDefault() },
    /** The shop's session; the instance's other parts read it too. */
    internal val session: SessionWindow = SessionWindow(settings, storage, clock),
) {

    /** Whether a request may go: computed from the state on every change. */
    private enum class Gate { WAIT, OPEN, BLOCKED, CLOSED }

    private val shopId = setup.shopId

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io + CoroutineExceptionHandler { _, throwable ->
        log(LogLevel.ERROR, LogCodes.SDK_LOG, "/init failed unexpectedly", throwable = throwable)
    })

    private val gate = MutableStateFlow(Gate.WAIT)

    /** How many answers to `/init` were taken: a wait for the next one watches it. */
    private val answers = MutableStateFlow(0L)

    /** Guards the fields below and every write an answer makes, so a restart never lets a stale one in. */
    private val lock = Any()
    private var job: Job? = null
    private var generation = 0
    private var released = false

    /** Storage prepared for the first `/init`; none goes before. */
    private var prepared = false

    /** Completed once the start's storage is prepared, or the instance released; for tests. */
    private val preparedSignal = CompletableDeferred<Unit>()

    /** `/init` is not sent on this device (a Google Play pre-launch report device). */
    @Volatile
    private var skipped = false

    /** Held by [holdForRestart] until [restart]: the customer is being deleted, no `/init` goes. */
    private var held = false
    private var usable = false
    private var fallbackLogged = false

    /**
     * An `/init` of this start succeeded. A later one — a new session's, the shop's check — that fails
     * changes nothing: the state stays, and the saved answer is not acted on again.
     */
    private var startAnswered = false

    /** A later `/init` of this start failed and is being repeated: requests go on what the instance has meanwhile. */
    private var laterFailing = false

    /** How many `/init` succeeded: [checkShopAndAwait] tells a success from another answer. */
    private var successes = 0L
    private var lastCheckAt: Long? = null
    private var foreground: Cancellable? = null
    private var network: Cancellable? = null

    /**
     * Ends an ordinary pause between repeats: the network coming back, the foreground, [checkShop]. A
     * server's `Retry-After` is waited in full all the same.
     */
    private val wake = Channel<Unit>(Channel.CONFLATED)

    /** [successes] when the last check of the shop went: a success since found the shop fine. */
    private var successesAtCheck = 0L

    /** How many `/init` went out; for tests. */
    @Volatile
    internal var sent = 0
        private set

    /** Whether an `/init` is on its way or waits for its next attempt; for tests. */
    internal val isRunning: Boolean
        get() = synchronized(lock) { job?.isActive == true }

    /** Returns once the `/init` running now, if any, has ended; for tests. */
    internal suspend fun awaitIdle() {
        synchronized(lock) { job }?.join()
    }

    /** Returns once [start] prepared the storage, and with it decided whether `/init` goes; for tests. */
    internal suspend fun awaitPrepared() = preparedSignal.await()

    /** Read once and reused: the advertising id goes with every `/init` of the launch. */
    @Volatile
    private var advertisingIdValue: String? = null

    /**
     * Sets the first state and sends `/init` once the shop's storage is prepared. The first state comes
     * from what the preferences hold: a refused shop starts refused, a stored `did` ready at once
     * (decision 1). The rest — `needReInitialization`, the `did` of the config or of the saved answer,
     * the ended session — is disk work and runs on [CoreDispatchers.io].
     */
    fun start() {
        val ready = synchronized(lock) {
            if (released) return
            val first = when {
                storage.isRefused -> SdkState.REFUSED
                !setup.needReInitialization && settings.getDid().isNotEmpty() -> SdkState.READY
                else -> SdkState.INITIALIZING
            }
            publish(first)
            first == SdkState.READY
        }
        if (ready) markUsable(generation())
        val handle = lifecycle.addListener(::onForegroundChanged)
        val networkHandle = connectivity.addListener { connected -> if (connected) wake.trySend(Unit) }
        // A release that came meanwhile does not leave the observers behind.
        val kept = synchronized(lock) {
            if (released) false else true.also {
                foreground = handle
                network = networkHandle
            }
        }
        if (!kept) {
            handle.cancel()
            networkHandle.cancel()
            return
        }
        val start = generation()
        scope.launch { prepare(start) }
    }

    /** On [CoreDispatchers.io]: the disk work of the start, then the first `/init`. */
    private fun prepare(start: Int) {
        val becameReady = try {
            synchronized(lock) {
                if (released) return
                // A deletion that began meanwhile has the customer forgotten: nothing of theirs comes back.
                if (start == generation) prepareStorage()
                prepared = true
                val ready = state.current == SdkState.INITIALIZING && !held && settings.getDid().isNotEmpty()
                if (ready) publish(SdkState.READY)
                launchLocked()
                ready
            }
        } finally {
            preparedSignal.complete(Unit)
        }
        if (becameReady) markUsable(start)
    }

    /**
     * Waits without a limit until the instance is ready, or never will be: null once it is (it has a
     * `did`), otherwise `shopBlocked` or `notInitialized`. The push token waits here (`core/push.md`).
     */
    internal suspend fun ready(): SdkError? = errorOf(gate.first { it != Gate.WAIT })

    /**
     * [ready] within [timeoutMs]: what a push's event waits for. It needs a `did` and no session — the
     * server reads no `sid` in it — so it never starts one. `timeout` when the wait runs out.
     */
    internal suspend fun awaitReady(timeoutMs: Long): SdkError? {
        val verdict = withTimeoutOrNull(timeoutMs) { Verdict(ready()) } ?: return SdkError.Timeout
        return verdict.error
    }

    /** For a request with a result: [usable] within [timeoutMs]. */
    suspend fun awaitUsable(timeoutMs: Long): SdkError? {
        val verdict = withTimeoutOrNull(timeoutMs) { Verdict(usable()) } ?: return SdkError.Timeout
        return verdict.error
    }

    private class Verdict(val error: SdkError?)

    /**
     * Waits, without a limit (the caller sets the deadline), until a request may go: the instance is
     * ready, and when the session's window has ended, the `/init` of a new session ([renewSession]) has
     * answered. The request then goes with the new `sid`, or, when that `/init` failed and the instance
     * works on what it has, without one (decision 1). Null when it may go; otherwise `shopBlocked` or
     * `notInitialized`.
     */
    internal suspend fun usable(): SdkError? {
        var renewed = false
        while (true) {
            errorOf(gate.first { it != Gate.WAIT })?.let { return it }
            if (renewed || session.isAlive()) return null
            val seen = answers.value
            if (!renewSession()) return null
            // The answer, or the gate closing (a release, a deletion's hold) ends the wait.
            combine(answers, gate) { count, current -> count > seen || current != Gate.OPEN }.first { it }
            renewed = true
        }
    }

    /**
     * Starts a new session when the window of the current one has ended: its `sid` is forgotten and
     * `/init` goes, unless one is on its way. True when a caller should wait for the answer: the instance
     * is [SdkState.READY] and `/init` is out. While `/init` is being repeated ([SdkState.RETRYING]) the
     * instance works on what it has and nobody waits; a refused or released instance renews nothing.
     */
    internal fun renewSession(): Boolean = synchronized(lock) {
        if (released || skipped || setup.pushOnly || state.current == SdkState.REFUSED || session.isAlive()) return false
        session.forgetIfEnded()
        launchLocked()
        // Before the storage is prepared the start's own /init is about to go: that is the one to wait for.
        // A new session's /init that failed and is repeated is not waited for: requests go on what there is.
        state.current == SdkState.READY && !laterFailing && (job?.isActive == true || !prepared)
    }

    /** What [checkShopAndAwait] learnt of the shop. */
    internal sealed interface ShopCheck {
        /** A new `/init` succeeded: the shop is fine. */
        object Fine : ShopCheck

        /** The shop refused the SDK. */
        object Refused : ShopCheck

        /** The instance was released. */
        object Released : ShopCheck

        /**
         * A check less than 10 minutes ago found the shop fine and it is ready: this refusal is the
         * service's, not the shop's.
         */
        object RecentlyFine : ShopCheck

        /**
         * No answer told: the check could not go yet — [retryInMs] until it may, after one less than 10
         * minutes ago that did not find the shop fine — or its answer did not come in time or was not a
         * success (null).
         */
        class Unknown(val retryInMs: Long?) : ShopCheck
    }

    /**
     * Asks the server about the shop after a `402`, `403` or `404` to an event ([checkShop]), and waits
     * up to [timeoutMs] for the answer of the `/init` that went, or of the one already on its way. Only a
     * new answer tells: [ShopCheck.Fine] when it was a success, [ShopCheck.Refused] when the shop refused;
     * a check less than 10 minutes ago that found the shop fine is [ShopCheck.RecentlyFine]; a wait that
     * ends first, a failure, or a check that may not go yet is [ShopCheck.Unknown].
     */
    internal suspend fun checkShopAndAwait(timeoutMs: Long): ShopCheck {
        val seen: Long
        val seenSuccesses: Long
        synchronized(lock) {
            if (released) return ShopCheck.Released
            if (state.current == SdkState.REFUSED) return ShopCheck.Refused
            // Only the app's own instance asks the server: the event waits for it.
            if (setup.pushOnly) return ShopCheck.Unknown(CHECK_SHOP_INTERVAL_MS)
            seen = answers.value
            seenSuccesses = successes
            val went = checkShop()
            if (!went && job?.isActive != true) {
                val last = lastCheckAt
                val wait = if (last == null) null else (CHECK_SHOP_INTERVAL_MS - (clock.nowMillis() - last)).takeIf { it > 0 }
                // Checked less than 10 minutes ago, and fine.
                if (wait != null && state.current == SdkState.READY && successes > successesAtCheck) return ShopCheck.RecentlyFine
                return ShopCheck.Unknown(wait)
            }
        }
        withTimeoutOrNull(timeoutMs) {
            combine(answers, gate) { count, current -> count > seen || current == Gate.CLOSED }.first { it }
        }
        return synchronized(lock) {
            when {
                released -> ShopCheck.Released
                state.current == SdkState.REFUSED -> ShopCheck.Refused
                successes > seenSuccesses -> ShopCheck.Fine
                else -> ShopCheck.Unknown(null)
            }
        }
    }

    /** Null for an open gate, otherwise why a request may not go; [Gate.WAIT] is never decided. */
    private fun errorOf(decided: Gate): SdkError? = when (decided) {
        Gate.BLOCKED -> SdkError.ShopBlocked
        Gate.CLOSED -> SdkError.NotInitialized
        else -> null
    }

    /**
     * Asks the server about the shop once, as the queue does after a `402`, `403` or `404` to an event:
     * not more often than every 10 minutes, not while `/init` is on its way and not for a refused shop.
     * True when `/init` went out.
     */
    internal fun checkShop(): Boolean = synchronized(lock) {
        if (released || held || !prepared || state.current == SdkState.REFUSED) return false
        if (job?.isActive == true) {
            // Being repeated: an ordinary pause ends, and the next attempt's answer is the check's.
            wake.trySend(Unit)
            return false
        }
        val now = clock.nowMillis()
        val last = lastCheckAt
        if (last != null && now - last < CHECK_SHOP_INTERVAL_MS) return false
        lastCheckAt = now
        successesAtCheck = successes
        launchLocked()
    }

    /**
     * Stops the current start before `deleteUserCredentials()` wipes the customer: the `/init` on its
     * way is cancelled and its answer will not be taken, requests wait for the next `did`, and no
     * `/init` goes — not on coming back to the foreground, not on [checkShop] — until [restart].
     */
    fun holdForRestart(): Unit = synchronized(lock) {
        if (released) return
        generation++
        held = true
        job?.cancel()
        job = null
        usable = false
        fallbackLogged = false
        startAnswered = false
        laterFailing = false
        publish(SdkState.INITIALIZING)
    }

    /** Starts over as a new device after [holdForRestart]: `/init` goes without a `did`. */
    fun restart(): Unit = synchronized(lock) {
        if (released) return
        held = false
        launchLocked()
    }

    /** Stops for good: `/init` is cancelled, waiting requests and later ones get `notInitialized`. */
    fun release() {
        val listening = synchronized(lock) {
            if (released) return
            released = true
            generation++
            job?.cancel()
            job = null
            gate.value = Gate.CLOSED
            network?.cancel()
            network = null
            foreground.also { foreground = null }
        }
        listening?.cancel()
        scope.cancel()
        preparedSignal.complete(Unit)
    }

    private fun generation(): Int = synchronized(lock) { generation }

    /** Must hold [lock]. */
    private fun prepareStorage() {
        if (setup.needReInitialization) {
            storage.forgetDevice()
            log(
                LogLevel.INFO,
                LogCodes.DEVICE_ID_RESET,
                "needReInitialization: the did is forgotten and /init asks for a new one",
                mapOf("reason" to LogReasons.NEED_REINITIALIZATION),
            )
        } else {
            storage.adoptConfiguredDid(setup.deviceId, settings)
        }
        // The saved answer's did fills a shop that lost its own; it never replaces one. After a wipe
        // (deleteUserCredentials, needReInitialization) whose file did not go, it is the forgotten one.
        if (settings.getDid().isEmpty() && !storage.isWiped) {
            storage.initAnswer()?.optString(UserBasicParams.DID)?.takeIf { it.isNotEmpty() }?.let(settings::updateDid)
        }
        forgetStaleSession()
    }

    /**
     * Must hold [lock]. Sends `/init` unless it is on its way already, the storage is not prepared yet or
     * a deletion holds the instance; true when it went.
     */
    private fun launchLocked(): Boolean {
        if (released || held || !prepared || setup.pushOnly || job?.isActive == true) return false
        val start = generation
        job = scope.launch { run(start) }
        return true
    }

    private sealed interface Step {
        object Done : Step
        class Answered(val answer: JSONObject) : Step
        class Retry(val delayMs: Long, val waitForNetwork: Boolean, val fallback: Boolean, val serverNamed: Boolean) : Step
    }

    private suspend fun run(start: Int) {
        if (skipped || setup.skipInit()) {
            if (!skipped) {
                skipped = true
                log(LogLevel.INFO, LogCodes.INIT_SKIPPED, "/init is not sent on a Google Play pre-launch report device",
                    mapOf("reason" to LogReasons.PRE_LAUNCH_REPORT))
            }
            return
        }
        var attempt = 0
        while (true) {
            synchronized(lock) {
                if (start != generation) return
                forgetStaleSession()
            }
            sent++
            val result = send(params())
            val step = synchronized(lock) {
                // An answer to a start that restart() or release() ended belongs to nobody.
                if (start != generation || released) return
                take(result, attempt).also { step ->
                    answers.value = answers.value + 1
                    // What follows an answer is the instance's work, not /init: a new one may go meanwhile.
                    if (step !is Step.Retry) job = null
                }
            }
            when (step) {
                Step.Done -> return
                is Step.Answered -> {
                    answered(start, step.answer, fresh = true)
                    markUsable(start)
                    return
                }
                is Step.Retry -> {
                    if (step.fallback) {
                        log(LogLevel.WARNING, LogCodes.INIT_SAVED_ANSWER, "The SDK works on the did it has while /init is repeated")
                        val saved = storage.initAnswer() ?: JSONObject()
                        answered(start, saved, fresh = false)
                        markUsable(start)
                    }
                    if (step.waitForNetwork) {
                        waitForNetwork()
                    } else {
                        pause(step.delayMs, step.serverNamed)
                        attempt++
                    }
                }
            }
        }
    }

    /** Must hold [lock]. Acts on one answer to `/init`. */
    private fun take(result: HttpResult, attempt: Int): Step {
        when (result) {
            is HttpResult.Success -> {
                val answer = result.body as? JSONObject
                val did = answer?.optString(UserBasicParams.DID).orEmpty()
                if (answer != null && did.isNotEmpty()) {
                    succeed(answer, did)
                    return Step.Answered(answer)
                }
                log(LogLevel.WARNING, LogCodes.DECODING_FAILED, "The answer to GET init has no did", INIT_CONTEXT)
                return retry(attempt, mapOf("error" to ERROR_DECODING), retryAfterMs = null)
            }
            is HttpResult.Failure -> {
                val error = result.error
                if (error is SdkError.Http) {
                    val code = error.code
                    return when {
                        code in SHOP_REFUSALS || (code in 400..499 && code !in REPEATED_4XX) -> refuse(code)
                        else -> retry(attempt, mapOf("status" to code), result.retryAfterMs.takeIf { code in RETRY_AFTER_CODES })
                    }
                }
                // A cancel the SDK did not make (an interceptor, the network layer): this start is still
                // current, so /init is repeated as after any other failure. Its own cancels never get here.
                return retry(attempt, mapOf("error" to errorName(error)), retryAfterMs = null)
            }
        }
    }

    /** Must hold [lock]. */
    private fun succeed(answer: JSONObject, did: String) {
        if (settings.getDid().isEmpty()) settings.updateDid(did)
        // The server's sid replaces whatever went: a sid an earlier release made goes out only once.
        val seance = answer.optString(UserBasicParams.SEANCE)
        if (seance.isNotEmpty()) session.start(seance) else session.startWithoutSid()
        storage.saveInitAnswer(answer)
        // The answer on disk is the new customer's now.
        if (storage.isWiped) storage.isWiped = false
        if (storage.isRefused) storage.isRefused = false
        startAnswered = true
        laterFailing = false
        successes++
        // A later failure of this start logs init_saved_answer again.
        fallbackLogged = false
        publish(SdkState.READY)
        log(LogLevel.INFO, LogCodes.INIT_SUCCEEDED, "/init succeeded")
        if (advertisingIdValue != null) log(LogLevel.DEBUG, LogCodes.ADVERTISING_ID_SENT, "Advertising id sent with /init")
    }

    /** Must hold [lock]. */
    private fun refuse(code: Int): Step {
        storage.isRefused = true
        publish(SdkState.REFUSED)
        log(LogLevel.ERROR, LogCodes.INIT_REFUSED, "The shop refused the SDK: calls fail with shopBlocked until /init succeeds on a later launch",
            mapOf("status" to code))
        if (code !in SHOP_REFUSALS) {
            log(LogLevel.ERROR, LogCodes.INIT_FAILED, "/init was rejected: the SDK's request is invalid", mapOf("status" to code))
        }
        return Step.Done
    }

    /** Must hold [lock]. */
    private fun retry(attempt: Int, cause: Map<String, Any?>, retryAfterMs: Long?): Step.Retry {
        // A shop refused at an earlier launch stays refused until the server says otherwise. A failed
        // later /init leaves the state as this start's answer set it.
        if (state.current != SdkState.REFUSED && !startAnswered) publish(SdkState.RETRYING)
        if (startAnswered) laterFailing = true
        val hasDid = settings.getDid().isNotEmpty()
        val waitForNetwork = !hasDid && !connectivity.isConnected
        val delayMs = backoff.delayMs(attempt, retryAfterMs)
        log(
            LogLevel.WARNING,
            LogCodes.INIT_RETRY,
            if (waitForNetwork) "/init failed and waits for the network" else "/init failed and is repeated",
            cause + mapOf("attempt" to attempt + 1) +
                (if (waitForNetwork) mapOf("reason" to LogReasons.OFFLINE) else mapOf("delay_ms" to delayMs)),
        )
        val fallback = !startAnswered && state.current == SdkState.RETRYING && hasDid && !fallbackLogged
        if (fallback) fallbackLogged = true
        return Step.Retry(delayMs, waitForNetwork, fallback, serverNamed = retryAfterMs != null)
    }

    /** Must hold [lock]. */
    private fun publish(next: SdkState) {
        state.set(next)
        gate.value = when (next) {
            SdkState.READY -> Gate.OPEN
            SdkState.RETRYING -> if (settings.getDid().isNotEmpty()) Gate.OPEN else Gate.WAIT
            SdkState.REFUSED -> Gate.BLOCKED
            SdkState.INITIALIZING -> Gate.WAIT
            SdkState.NOT_INITIALIZED -> if (released) Gate.CLOSED else Gate.WAIT
        }
    }

    /**
     * Hands an answer of [start] to the instance (its popup, its flags), unless a restart or a release
     * ended that start meanwhile: under [lock], so the forgotten customer's popup is never shown.
     */
    private fun answered(start: Int, answer: JSONObject, fresh: Boolean) {
        synchronized(lock) {
            if (start != generation || released) return
            hook("onAnswer") { hooks.onAnswer(answer, fresh) }
        }
    }

    /** Tells the instance it can send, once per start, and only while that start is the current one. */
    private fun markUsable(start: Int) {
        synchronized(lock) {
            if (start != generation || released || usable) return
            usable = true
            hook("onUsable") { hooks.onUsable() }
        }
    }

    /** A `sid` whose window ended (two hours without activity) is not sent: the server starts a new session. */
    private fun forgetStaleSession() {
        session.forgetIfEnded()
    }

    /** Heard on the main thread; handled on [CoreDispatchers.io], where waiting for [lock] blocks nothing. */
    private fun onForegroundChanged(foreground: Boolean) {
        if (!foreground) return
        wake.trySend(Unit)
        scope.launch { onForeground() }
    }

    /** Coming back after the window ended starts a new session. */
    private fun onForeground() {
        renewSession()
    }

    /** The fields of `/init` beside the common ones: `tz`, the advertising id, the extras. */
    private suspend fun params(): JSONObject {
        val params = JSONObject()
        // The offset now, summer time included.
        params.put(PARAM_TZ, (timeZone().getOffset(clock.nowMillis()) / HOUR_MS).toString())
        advertisingId()?.let { params.put(PARAM_ADVERTISING_ID, it) }
        setup.extraParams().forEach { (key, value) -> params.put(key, value) }
        return params
    }

    private suspend fun advertisingId(): String? {
        val read = setup.advertisingId ?: return null
        advertisingIdValue?.let { return it }
        return try {
            read()?.takeIf { it.isNotBlank() }?.also { advertisingIdValue = it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(LogLevel.WARNING, LogCodes.ADVERTISING_ID_FAILED, "The advertising id could not be read", throwable = e)
            null
        }
    }

    /**
     * A pause between repeats. A server's `Retry-After` ([serverNamed]) is waited in full; an ordinary one
     * ends when the network comes back, the app comes to the foreground or the shop is to be checked.
     */
    private suspend fun pause(delayMs: Long, serverNamed: Boolean) {
        // What woke the machine before this pause began is old news.
        while (wake.tryReceive().isSuccess) Unit
        if (serverNamed) {
            delay(delayMs)
        } else {
            withTimeoutOrNull(delayMs) { wake.receive() }
        }
    }

    /** Waits for the network; looks again every few minutes in case a change went unheard. */
    private suspend fun waitForNetwork() {
        while (!connectivity.isConnected) {
            withTimeoutOrNull(NETWORK_RECHECK_MS) { awaitNetwork() }
        }
    }

    private suspend fun awaitNetwork() {
        val back = CompletableDeferred<Unit>()
        val handle = connectivity.addListener { connected -> if (connected) back.complete(Unit) }
        try {
            if (!connectivity.isConnected) back.await()
        } finally {
            handle.cancel()
        }
    }

    private inline fun hook(name: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            log(LogLevel.ERROR, LogCodes.SDK_LOG, "Acting on /init failed: $name", throwable = e)
        }
    }

    private fun errorName(error: SdkError): String = when (error) {
        SdkError.Network -> "network"
        SdkError.Timeout -> "timeout"
        SdkError.Decoding -> ERROR_DECODING
        SdkError.Cancelled -> "cancelled"
        else -> "failed"
    }

    private fun log(level: LogLevel, code: String, message: String, context: Map<String, Any?> = emptyMap(), throwable: Throwable? = null) =
        SdkLogs.log(level, code, message, shopId, context, throwable)

    internal companion object {
        /** Two hours without activity end a session ([SessionWindow.WINDOW_MS]). */
        const val SESSION_WINDOW_MS = SessionWindow.WINDOW_MS

        /** [checkShop] asks the server at most this often. */
        const val CHECK_SHOP_INTERVAL_MS = 10 * 60 * 1000L

        /** How often a wait for the network looks again by itself. */
        const val NETWORK_RECHECK_MS = 5 * 60 * 1000L

        private const val HOUR_MS = 3_600_000
        private const val PARAM_TZ = "tz"
        private const val PARAM_ADVERTISING_ID = "android_advertising_id"
        private const val ERROR_DECODING = "decoding"
        private val INIT_CONTEXT = mapOf("method" to "GET", "path" to CoreHttpClient.INIT_PATH)

        /** The shop is not there for the SDK: unpaid, off or unknown. */
        private val SHOP_REFUSALS = setOf(402, 403, 404)

        /** `4xx` answers that are repeated rather than taken as a refusal. */
        private val REPEATED_4XX = setOf(408, 410, 417, 426, 429)

        /** Answers whose `Retry-After` sets the pause. */
        private val RETRY_AFTER_CODES = setOf(429, 503)
    }
}
