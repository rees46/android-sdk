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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
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

/** Holds a request until the instance may send it. */
internal fun interface ReadyGate {
    /** Null once the request may go; otherwise why it may not: `shopBlocked`, `notInitialized` or `timeout`. */
    suspend fun awaitReady(timeoutMs: Long): SdkError?
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
 * - **Later `/init`.** On coming back to the foreground more than two hours after the last activity, and
 *   on [checkShop]. A refused shop is not asked again before the next launch.
 *
 * Requests with a result wait in [awaitReady]. A stale answer (an `/init` of a start [restart] or
 * [release] ended) is dropped.
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
) : ReadyGate {

    /** Whether a request may go: computed from the state on every change. */
    private enum class Gate { WAIT, OPEN, BLOCKED, CLOSED }

    private val shopId = setup.shopId

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io + CoroutineExceptionHandler { _, throwable ->
        log(LogLevel.ERROR, LogCodes.SDK_LOG, "/init failed unexpectedly", throwable = throwable)
    })

    private val gate = MutableStateFlow(Gate.WAIT)

    /** Guards the fields below and every write an answer makes, so a restart never lets a stale one in. */
    private val lock = Any()
    private var job: Job? = null
    private var generation = 0
    private var released = false
    private var usable = false
    private var fallbackLogged = false
    private var lastCheckAt: Long? = null
    private var foreground: Cancellable? = null

    /** Read once and reused: the advertising id goes with every `/init` of the launch. */
    @Volatile
    private var advertisingIdValue: String? = null

    /** Prepares the shop's storage, sets the first state and sends `/init`. */
    fun start() {
        val ready = synchronized(lock) {
            if (released) return
            prepareStorage()
            val first = when {
                storage.isRefused -> SdkState.REFUSED
                settings.getDid().isNotEmpty() -> SdkState.READY
                else -> SdkState.INITIALIZING
            }
            publish(first)
            first == SdkState.READY
        }
        if (ready) markUsable(generation())
        foreground = lifecycle.addListener(::onForegroundChanged)
        synchronized(lock) { launchLocked() }
    }

    override suspend fun awaitReady(timeoutMs: Long): SdkError? {
        val decided = withTimeoutOrNull(timeoutMs) { gate.first { it != Gate.WAIT } } ?: return SdkError.Timeout
        return when (decided) {
            Gate.OPEN -> null
            Gate.BLOCKED -> SdkError.ShopBlocked
            Gate.CLOSED -> SdkError.NotInitialized
            Gate.WAIT -> SdkError.Timeout
        }
    }

    /**
     * Asks the server about the shop once, as the queue does after a `402`, `403` or `404` to an event:
     * not more often than every 10 minutes, not while `/init` is on its way and not for a refused shop.
     * True when `/init` went out.
     */
    internal fun checkShop(): Boolean = synchronized(lock) {
        if (released || state.current == SdkState.REFUSED || job?.isActive == true) return false
        val now = clock.nowMillis()
        val last = lastCheckAt
        if (last != null && now - last < CHECK_SHOP_INTERVAL_MS) return false
        lastCheckAt = now
        launchLocked()
    }

    /**
     * Stops the current start before `deleteUserCredentials()` wipes the customer: the `/init` on its
     * way is cancelled and its answer will not be taken, and requests wait for the next `did`.
     */
    fun holdForRestart(): Unit = synchronized(lock) {
        if (released) return
        generation++
        job?.cancel()
        job = null
        usable = false
        fallbackLogged = false
        publish(SdkState.INITIALIZING)
    }

    /** Starts over as a new device after [holdForRestart]: `/init` goes without a `did`. */
    fun restart(): Unit = synchronized(lock) {
        if (released) return
        launchLocked()
    }

    /** Stops for good: `/init` is cancelled, waiting requests and later ones get `notInitialized`. */
    fun release() {
        synchronized(lock) {
            if (released) return
            released = true
            generation++
            job?.cancel()
            job = null
            gate.value = Gate.CLOSED
        }
        foreground?.cancel()
        scope.cancel()
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
            val configured = setup.deviceId?.trim().orEmpty()
            if (configured.isNotEmpty() && settings.getDid().isEmpty()) settings.updateDid(configured)
        }
        // The saved answer's did fills a shop that lost its own; it never replaces one.
        if (settings.getDid().isEmpty()) {
            storage.initAnswer()?.optString(UserBasicParams.DID)?.takeIf { it.isNotEmpty() }?.let(settings::updateDid)
        }
        forgetStaleSession()
    }

    /** Must hold [lock]. Sends `/init` unless it is on its way already; true when it went. */
    private fun launchLocked(): Boolean {
        if (released || job?.isActive == true) return false
        val start = generation
        job = scope.launch { run(start) }
        return true
    }

    private sealed interface Step {
        object Done : Step
        class Answered(val answer: JSONObject) : Step
        class Retry(val delayMs: Long, val waitForNetwork: Boolean, val fallback: Boolean) : Step
    }

    private suspend fun run(start: Int) {
        if (setup.skipInit()) {
            log(LogLevel.INFO, LogCodes.INIT_SKIPPED, "/init is not sent on a Google Play pre-launch report device",
                mapOf("reason" to LogReasons.PRE_LAUNCH_REPORT))
            return
        }
        var attempt = 0
        while (true) {
            synchronized(lock) {
                if (start != generation) return
                forgetStaleSession()
            }
            val result = send(params())
            val step = synchronized(lock) {
                // An answer to a start that restart() or release() ended belongs to nobody.
                if (start != generation || released) return
                take(result, attempt)
            }
            when (step) {
                Step.Done -> return
                is Step.Answered -> {
                    hook("onAnswer") { hooks.onAnswer(step.answer, fresh = true) }
                    markUsable(start)
                    return
                }
                is Step.Retry -> {
                    if (step.fallback) {
                        log(LogLevel.WARNING, LogCodes.INIT_SAVED_ANSWER, "The SDK works on the did it has while /init is repeated")
                        val saved = storage.initAnswer() ?: JSONObject()
                        hook("onAnswer") { hooks.onAnswer(saved, fresh = false) }
                        markUsable(start)
                    }
                    if (step.waitForNetwork) {
                        waitForNetwork()
                    } else {
                        pause(step.delayMs)
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
                if (error == SdkError.Cancelled) return Step.Done
                return retry(attempt, mapOf("error" to errorName(error)), retryAfterMs = null)
            }
        }
    }

    /** Must hold [lock]. */
    private fun succeed(answer: JSONObject, did: String) {
        if (settings.getDid().isEmpty()) settings.updateDid(did)
        answer.optString(UserBasicParams.SEANCE).takeIf { it.isNotEmpty() }?.let(settings::updateSid)
        storage.saveInitAnswer(answer)
        if (storage.isRefused) storage.isRefused = false
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
        // A shop refused at an earlier launch stays refused until the server says otherwise.
        if (state.current != SdkState.REFUSED) publish(SdkState.RETRYING)
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
        val fallback = state.current == SdkState.RETRYING && hasDid && !fallbackLogged
        if (fallback) fallbackLogged = true
        return Step.Retry(delayMs, waitForNetwork, fallback)
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

    /** Tells the instance it can send, once per start. */
    private fun markUsable(start: Int) {
        val first = synchronized(lock) {
            if (start != generation || released || usable) return
            usable = true
            true
        }
        if (first) hook("onUsable") { hooks.onUsable() }
    }

    /** A `sid` whose window ended (two hours without activity) is not sent: the server starts a new session. */
    private fun forgetStaleSession() {
        if (settings.getSid().isEmpty()) return
        if (clock.nowMillis() - settings.getSidLastActTime() > SESSION_WINDOW_MS) storage.forgetSession()
    }

    private fun onForegroundChanged(foreground: Boolean) {
        if (!foreground) return
        synchronized(lock) {
            if (released || state.current == SdkState.REFUSED || job?.isActive == true) return
            if (clock.nowMillis() - settings.getSidLastActTime() <= SESSION_WINDOW_MS) return
            launchLocked()
        }
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

    /** A pause between repeats; the network coming back ends a pause it set off. */
    private suspend fun pause(delayMs: Long) {
        if (connectivity.isConnected) {
            delay(delayMs)
        } else {
            withTimeoutOrNull(delayMs) { awaitNetwork() }
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
        else -> "failed"
    }

    private fun log(level: LogLevel, code: String, message: String, context: Map<String, Any?> = emptyMap(), throwable: Throwable? = null) =
        SdkLogs.log(level, code, message, shopId, context, throwable)

    internal companion object {
        /** Two hours without activity end a session. */
        const val SESSION_WINDOW_MS = 2 * 60 * 60 * 1000L

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
