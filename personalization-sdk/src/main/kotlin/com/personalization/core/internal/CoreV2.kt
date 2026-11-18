package com.personalization.core.internal

import com.personalization.LogLevel
import com.personalization.api.OnApiCallbackListener
import com.personalization.core.SdkError
import com.personalization.core.SdkException
import com.personalization.logs.LogCodes
import com.personalization.logs.LogReasons
import com.personalization.logs.SdkLogs
import com.personalization.sdk.domain.models.NetworkMethod
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The new core: the instance's requests go through [CoreHttpClient], and its `/init` and state are
 * [CoreInit]'s, its session [SessionWindow]'s. A request waits for the instance to be ready within its
 * own read timeout, and for the `/init` of a new session when the window of the last one ended: a
 * refused shop fails it with `shopBlocked` (the old callbacks get `402`), a wait that runs out with
 * `timeout`. A request with a result does not extend the session. Events (`/push`, `/push/custom`,
 * `track/stories`, `track/slider`, `popup/showed`) go to the [EventQueue] instead, once it is set.
 *
 * Every request ends in exactly one callback, on a background thread as today: the answer, or the
 * error mapped to the old `onError(code, msg)` by [LegacyErrors]. [request] is the same path for the
 * namespaces: it returns the outcome with its [SdkError] instead. [release] cancels the requests in
 * flight (`cancelled`) and stops `/init`, and a request made after it fails with `notInitialized`.
 */
internal class CoreV2(
    internal val client: CoreHttpClient,
    private val shopId: String?,
    dispatchers: CoreDispatchers = CoreDispatchers.DEFAULT,
) : Core {

    override val isV2: Boolean = true

    private val initFlow = MutableStateFlow<CoreInit?>(null)

    /**
     * The instance's `/init`; a request waits on it. Set before the core takes requests: one that comes
     * while it is not set yet waits for it like for the instance to be ready.
     */
    internal var init: CoreInit?
        get() = initFlow.value
        set(value) {
            initFlow.value = value
        }

    /** The instance's event queue; set with [init], before the core takes requests. */
    @Volatile
    internal var queue: EventQueue? = null

    /** How long a request waits for the instance to be ready: its own read timeout. */
    private val readyWaitMs: Long = client.timeouts.readMs

    /** Nothing a request does may reach the app's process as an exception. */
    private val uncaught = CoroutineExceptionHandler { _, throwable ->
        SdkLogs.log(LogLevel.ERROR, LogCodes.SDK_LOG, "A request of the core failed unexpectedly", shopId, throwable = throwable)
    }

    /** The instance's requests; [release] cancels them all. */
    private val requests = CoroutineScope(SupervisorJob() + dispatchers.io + uncaught)

    /** Answers that do not come from a running request: a cancelled one, one after [release]. */
    private val endings = CoroutineScope(SupervisorJob() + dispatchers.io + uncaught)

    private val delivery = LegacyDelivery { level, code, message, throwable ->
        SdkLogs.log(level, code, message, shopId, throwable = throwable)
    }

    @Volatile
    private var released = false

    override fun send(method: NetworkMethod, params: JSONObject, listener: OnApiCallbackListener?) {
        val answered = AtomicBoolean(false)
        // Delivers the result unless the request has had its answer; true when this one was it.
        val answer = { result: HttpResult ->
            answered.compareAndSet(false, true).also { first ->
                if (first && listener != null) deliver(listener, result)
            }
        }
        if (released) {
            endings.launch { answer(HttpResult.Failure(SdkError.NotInitialized)) }
            return
        }
        val queue = queue
        if (queue != null && method is NetworkMethod.POST && method.method in EVENT_PATHS) {
            queue.accept(method.method, params, listener)
            return
        }
        sendNow(method, params, answer = answer)
    }

    /**
     * Takes an event of [path] for the queue without an answer listener: [accepted] hears, once, null when
     * it is written to disk (or, when the disk refused it, sent once as a request), otherwise why it was
     * refused — `notInitialized` after [release], `shopBlocked` for a refused shop.
     */
    fun accept(path: String, params: JSONObject, accepted: (SdkError?) -> Unit) {
        val queue = queue
        if (released || queue == null) {
            endings.launch { accepted(SdkError.NotInitialized) }
            return
        }
        queue.accept(path, params, listener = null, accepted = accepted)
    }

    /**
     * Sends an event the queue's disk would not take, once, as a request, by the queue's rules: a push event
     * waits only for the instance to be ready — it neither waits for a session nor starts one, and asks
     * `/init` nothing — and a tracking event as any request does.
     */
    internal fun sendDirectly(path: String, params: JSONObject, listener: OnApiCallbackListener?) {
        val answered = AtomicBoolean(false)
        val push = path in EventQueue.PUSH_EVENT_PATHS
        sendNow(NetworkMethod.POST(path), params, needsSession = !push) { result: HttpResult ->
            answered.compareAndSet(false, true).also { first -> if (first && listener != null) deliver(listener, result) }
        }
    }

    private fun sendNow(method: NetworkMethod, params: JSONObject, needsSession: Boolean = true, answer: (HttpResult) -> Boolean) {
        val request = requests.launch { answer(perform(method, params, needsSession)) }
        // Cancelled before or while it ran: the request still gets its one answer.
        request.invokeOnCompletion { cause ->
            if (cause != null) endings.launch {
                if (answer(HttpResult.Failure(SdkError.Cancelled))) {
                    SdkLogs.log(LogLevel.DEBUG, LogCodes.REQUEST_CANCELLED, "${method.type} ${method.method}: cancelled", shopId,
                        mapOf("method" to method.type, "path" to method.method))
                }
            }
        }
    }

    /**
     * A request with a result, for the namespaces: it waits for the instance as [send] does, within
     * [readTimeoutMs] (the client's read timeout without one), goes once and returns its outcome. Nothing
     * repeats it: whether to ask again is the caller's choice.
     *
     * After [release] it fails with `notInitialized` without a request, and [release] ends one in flight
     * with `cancelled`. Cancelling the caller cancels the request too, and the caller gets its own
     * [CancellationException], as a cancelled coroutine does.
     *
     * A request with the shop's [secret] carries `shop_id` and none of the device's fields
     * ([CoreHttpClient.send]); it waits for the instance to be ready, not for a session.
     */
    suspend fun request(method: NetworkMethod, params: JSONObject, readTimeoutMs: Long? = null, secret: Boolean = false): HttpResult {
        if (released) return HttpResult.Failure(SdkError.NotInitialized)
        val call = requests.async { perform(method, params, needsSession = !secret, readTimeoutMs = readTimeoutMs, secret = secret) }
        return try {
            call.await()
        } catch (e: CancellationException) {
            call.cancel()
            // The caller was cancelled: its cancellation goes on.
            currentCoroutineContext().ensureActive()
            // The instance was released while the request waited or went.
            SdkLogs.log(LogLevel.DEBUG, LogCodes.REQUEST_CANCELLED, "${method.type} ${method.method}: cancelled", shopId,
                mapOf("method" to method.type, "path" to method.method))
            HttpResult.Failure(SdkError.Cancelled)
        }
    }

    /**
     * One request: waits for the instance — with [needsSession] for a live session too — within
     * [readTimeoutMs] or the client's read timeout, then sends. Every failure is a result.
     */
    private suspend fun perform(
        method: NetworkMethod,
        params: JSONObject,
        needsSession: Boolean = true,
        readTimeoutMs: Long? = null,
        secret: Boolean = false,
    ): HttpResult {
        val waitMs = readTimeoutMs ?: readyWaitMs
        val waited = if (needsSession) awaitInstance(waitMs) else awaitReady(waitMs)
        if (waited != null) {
            if (waited == SdkError.Timeout) {
                SdkLogs.log(LogLevel.WARNING, LogCodes.REQUEST_FAILED, "${method.type} ${method.method}: the SDK is not ready", shopId,
                    mapOf("method" to method.type, "path" to method.method, "reason" to LogReasons.NOT_READY))
            }
            return HttpResult.Failure(waited)
        }
        return try {
            client.send(method, params, readTimeoutMs = readTimeoutMs, secret = secret)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The client turns every failure into a result; this is the net under it.
            SdkLogs.log(LogLevel.WARNING, LogCodes.REQUEST_FAILED, "${method.type} ${method.method}: no answer", shopId,
                mapOf("method" to method.type, "path" to method.method), e)
            HttpResult.Failure(SdkError.Network)
        }
    }

    /**
     * Waits, within [waitMs] (the read timeout), for the instance's `/init` to be set, for the instance to
     * be ready and, when the session's window ended, for the `/init` of a new session ([CoreInit.usable]);
     * then the request gets its own full timeout. Null when it may go.
     */
    private suspend fun awaitInstance(waitMs: Long = readyWaitMs): SdkError? {
        val waited = withTimeoutOrNull(waitMs) {
            val current = initFlow.filterNotNull().first()
            Verdict(current.usable())
        }
        return if (waited == null) SdkError.Timeout else waited.error
    }

    /** [awaitInstance] for what needs only a `did`: the instance ready, whatever its session. Null when it may go. */
    private suspend fun awaitReady(waitMs: Long = readyWaitMs): SdkError? {
        val waited = withTimeoutOrNull(waitMs) {
            val current = initFlow.filterNotNull().first()
            Verdict(current.ready())
        }
        return if (waited == null) SdkError.Timeout else waited.error
    }

    /**
     * For the clients that make their own requests (popups v3, stories v3): the wait a request of the core
     * makes — for the instance and, when the session's window ended, for the `/init` of a new one — so they
     * go with the live `sid`. Null when they may go.
     */
    internal suspend fun awaitUsable(): SdkError? = awaitInstance()

    /** What a wait decided; null [error] when the request may go. */
    private class Verdict(val error: SdkError?)

    override fun release() = release(cancelRequests = true)

    /**
     * Stops the core: `/init` stops and later requests fail with `notInitialized`. With [cancelRequests]
     * the requests in flight are cancelled too; without, as when a push-only instance gives way to the
     * app's, they finish and get their answers.
     */
    fun release(cancelRequests: Boolean) {
        released = true
        if (cancelRequests) requests.cancel()
        init?.release()
        queue?.release(cancel = cancelRequests)
    }

    /** Returns once the events accepted before the call are written to disk ([EventQueue.awaitWrites]). */
    suspend fun awaitWrites() {
        queue?.awaitWrites()
    }

    /** Sends the queue now ([EventQueue.flush]). */
    suspend fun flush() {
        if (released) throw SdkException(SdkError.NotInitialized)
        queue?.flush()
    }

    /**
     * The events in the queue were the customer's: the queue is emptied — an event accepted before the
     * call goes, its listener hears `-1` — and its directory goes with the credentials while no event
     * can be accepted.
     */
    override suspend fun deleteUserCredentials(storage: ShopStorage): Boolean {
        val queue = queue ?: return storage.deleteUserCredentials(includeQueue = true)
        return queue.clear(LogReasons.DELETE_USER_CREDENTIALS, CLEARED) { storage.deleteUserCredentials(includeQueue = true) }
    }

    internal companion object {
        /** The paths whose events go through the queue. */
        val EVENT_PATHS: Set<String> get() = EventQueue.PATHS

        private val CLEARED = LegacyError(LegacyErrors.NO_ANSWER, "The event was deleted with the customer's data", SdkError.Cancelled)
    }

    private fun deliver(listener: OnApiCallbackListener, result: HttpResult) = when (result) {
        is HttpResult.Success -> delivery.success(listener, result.body)
        is HttpResult.Failure -> delivery.failure(listener, LegacyErrors.of(result.error, result.errorBody))
    }
}
