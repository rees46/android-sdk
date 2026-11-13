package com.personalization.core.internal

import com.personalization.LogLevel
import com.personalization.api.OnApiCallbackListener
import com.personalization.core.SdkError
import com.personalization.logs.LogCodes
import com.personalization.logs.LogReasons
import com.personalization.logs.SdkLogs
import com.personalization.sdk.domain.models.NetworkMethod
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The new core: the instance's requests go through [CoreHttpClient], and its `/init` and state are
 * [CoreInit]'s. A request waits for the instance to be ready within its own read timeout: a refused
 * shop fails it with `shopBlocked` (the old callbacks get `402`), a wait that runs out with `timeout`.
 * The session and the queue stay the legacy ones until the core takes them over.
 *
 * Every request ends in exactly one callback, on a background thread as today: the answer, or the
 * error mapped to the old `onError(code, msg)` by [LegacyErrors]. [release] cancels the requests in
 * flight (`cancelled`) and stops `/init`, and a request made after it fails with `notInitialized`.
 */
internal class CoreV2(
    internal val client: CoreHttpClient,
    private val shopId: String?,
    dispatchers: CoreDispatchers = CoreDispatchers.DEFAULT,
) : Core {

    override val isV2: Boolean = true

    /** The instance's `/init`, once it started; a request waits on it. */
    @Volatile
    internal var init: CoreInit? = null

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
        val request = requests.launch {
            val waited = init?.awaitReady(readyWaitMs)
            if (waited != null) {
                if (waited == SdkError.Timeout) {
                    SdkLogs.log(LogLevel.WARNING, LogCodes.REQUEST_FAILED, "${method.type} ${method.method}: the SDK is not ready", shopId,
                        mapOf("method" to method.type, "path" to method.method, "reason" to LogReasons.NOT_READY))
                }
                answer(HttpResult.Failure(waited))
                return@launch
            }
            val result = try {
                client.send(method, params)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The client turns every failure into a result; this is the net under it.
                SdkLogs.log(LogLevel.WARNING, LogCodes.REQUEST_FAILED, "${method.type} ${method.method}: no answer", shopId,
                    mapOf("method" to method.type, "path" to method.method), e)
                HttpResult.Failure(SdkError.Network)
            }
            answer(result)
        }
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

    override fun release() {
        released = true
        requests.cancel()
        init?.release()
    }

    /** The events in the queue were the customer's: its directory goes with the credentials. */
    override fun deleteUserCredentials(storage: ShopStorage): Boolean = storage.deleteUserCredentials(includeQueue = true)

    private fun deliver(listener: OnApiCallbackListener, result: HttpResult) = when (result) {
        is HttpResult.Success -> delivery.success(listener, result.body)
        is HttpResult.Failure -> LegacyErrors.of(result.error, result.errorBody).let { delivery.failure(listener, it.code, it.message) }
    }
}
