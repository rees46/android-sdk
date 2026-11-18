package com.personalization.core.internal

import com.personalization.LogLevel
import com.personalization.core.CompletionListener
import com.personalization.core.SdkError
import com.personalization.core.SdkException
import com.personalization.core.SdkResult
import com.personalization.logs.LogCodes
import com.personalization.logs.SdkLogs
import com.personalization.sdk.domain.models.NetworkMethod
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * The path every method of a namespace (`sdk.cart`, and those after it) takes: its arguments checked before
 * anything is asked ([checked]), then one request with a result over the new core ([request]) with its
 * answer read into the method's model, or one event through the new core's queue ([event]); and the
 * contract's two forms of a method — a suspending function and one that ends in a [CompletionListener].
 *
 * The namespaces run on the new core only, which the bridges start with `Rees46.initializeBridge`. On the
 * legacy core a call fails with [SdkError.NotInitialized] at once, without a request, and the log says why
 * (`core_flag_mismatch`, at warning); so it does on an instance never initialized or released. The core
 * decides before the arguments: on the legacy core every call ends the same way, whatever its arguments.
 *
 * What a call can end with: the value; [SdkError.InvalidArgument] for an argument the contract refuses,
 * without a request; [SdkError.ShopBlocked] for a refused shop; [SdkError.Timeout] when the instance was not
 * ready within the read timeout, or the answer did not come in it; [SdkError.Network] without a connection;
 * [SdkError.Http] for an answer outside `2xx` (`serverCode` stays null: the server sends no machine code
 * yet); [SdkError.Decoding] for a `2xx` answer that does not read into the model, `{"status": "error"}`
 * among them; [SdkError.Cancelled] when `release()` ends the request. Nothing repeats a request. A `402`,
 * `403` or `404` changes nothing in the instance: it may refuse one service, not the shop.
 */
internal class NamespaceCalls(
    private val core: () -> Core?,
    private val dispatchers: () -> CoreDispatchers,
    private val shopId: () -> String?,
) {

    /** Where the listener forms run; nothing they do reaches the app's process as an exception. */
    private val scope: CoroutineScope by lazy {
        CoroutineScope(SupervisorJob() + dispatchers().io + CoroutineExceptionHandler { _, throwable ->
            SdkLogs.log(LogLevel.ERROR, LogCodes.SDK_LOG, "A call of a namespace failed unexpectedly", shopId(), throwable = throwable)
        })
    }

    /**
     * What [build] makes of a method's arguments — the request's fields, a path, or nothing — for the
     * namespace method [call] (`cart.get`, for the log), once the call may go: off the new core it fails with
     * [SdkError.NotInitialized] first, whatever the arguments, logged as `core_flag_mismatch`; then a wrong
     * argument, which [build] reports with [invalidArgument], fails it with [SdkError.InvalidArgument], logged
     * as `invalid_argument` with the call — and [reason], for a method whose refusals have codes of their own
     * (`empty_code`) — never with an argument's value. Nothing is sent. The one check of the namespaces:
     * [request] and [event] make it too.
     *
     * @throws SdkException with the reason.
     */
    fun <T> checked(call: String, reason: String? = null, build: () -> T): T {
        newCore(call)
        return refusing(call, reason, build)
    }

    /**
     * Sends [params] to [method] for the namespace method [call] and reads the `2xx` answer with [read]: it
     * gets the answer's [JSONObject] or [org.json.JSONArray], or null for an answer without a body, and
     * returns the method's value, or null when the answer does not fit it (`decoding`). A method with a read
     * timeout of its own passes [readTimeoutMs]. A method with arguments builds [params] in [checked] first.
     * A server method whose [params] carry the shop's secret passes [secret]: the request then carries no
     * field of the device, and the secret reaches neither the log nor the app's network logger.
     *
     * @throws SdkException with the reason the call failed.
     */
    suspend fun <T : Any> request(
        call: String,
        method: NetworkMethod,
        params: JSONObject = JSONObject(),
        readTimeoutMs: Long? = null,
        secret: Boolean = false,
        read: (Any?) -> T?,
    ): T {
        return when (val result = newCore(call).request(method, params, readTimeoutMs, secret)) {
            is HttpResult.Failure -> throw SdkException(result.error)
            is HttpResult.Success -> readAnswer(call, method, result, read) ?: throw SdkException(SdkError.Decoding)
        }
    }

    /**
     * An event of the namespace method [call] (`tracking.bannerView`): the fields [fields] makes, checked as
     * [checked] checks them, to [path] through the new core's queue, which adds the common fields and sends
     * it. The queue gets it on the caller's thread, so events keep the order of the calls. [listener] hears
     * once, on the main thread: success once the queue accepted the event, or why it did not — what
     * [checked] says, [SdkError.NotInitialized] after `release()`, [SdkError.ShopBlocked] for a refused shop.
     * A refused event is not sent.
     */
    fun event(call: String, path: String, listener: CompletionListener<Unit>?, fields: () -> JSONObject) {
        val core: CoreV2
        val params: JSONObject
        try {
            core = newCore(call)
            params = refusing(call, null, fields)
        } catch (e: SdkException) {
            tell(listener, SdkResult.Failure(e.error))
            return
        }
        core.accept(path, params) { refused ->
            tell(listener, if (refused == null) SdkResult.Success(Unit) else SdkResult.Failure(refused))
        }
    }

    /**
     * The form of a method for Java and for code outside coroutines: runs [block], its suspending form, and
     * hands [listener] its value or its error, once, on the main thread ([tell]).
     */
    fun <T> complete(listener: CompletionListener<T>, block: suspend () -> T) {
        scope.launch {
            val result: SdkResult<T> = try {
                SdkResult.Success(block())
            } catch (e: SdkException) {
                SdkResult.Failure(e.error)
            } catch (e: CancellationException) {
                // The scope is never cancelled: a request's cancellation comes back as `cancelled` already.
                SdkResult.Failure(SdkError.Cancelled)
            } catch (e: Exception) {
                SdkLogs.log(LogLevel.ERROR, LogCodes.SDK_LOG, "A call of a namespace failed unexpectedly", shopId(), throwable = e)
                SdkResult.Failure(SdkError.Network)
            }
            hand(listener, result)
        }
    }

    /**
     * Hands [listener] the [result] it already has, once, on the main thread and never on the caller's
     * stack; nothing without a listener.
     */
    fun <T> tell(listener: CompletionListener<T>?, result: SdkResult<T>) {
        if (listener == null) return
        scope.launch { hand(listener, result) }
    }

    /** Calls [listener] on the main thread; an exception it throws is logged as `callback_failed` and goes no further. */
    private suspend fun <T> hand(listener: CompletionListener<T>, result: SdkResult<T>) {
        withContext(dispatchers().main) {
            try {
                listener.onComplete(result)
            } catch (e: Exception) {
                SdkLogs.log(LogLevel.ERROR, LogCodes.CALLBACK_FAILED, "The app's CompletionListener threw", shopId(), throwable = e)
            }
        }
    }

    /** The value [read] makes of a `2xx` answer; null, logged as `decoding_failed`, when it makes none. */
    private fun <T : Any> readAnswer(call: String, method: NetworkMethod, result: HttpResult.Success, read: (Any?) -> T?): T? {
        val body = result.body.takeUnless { result.empty }
        if (body is JSONObject && body.optString(STATUS) == STATUS_ERROR) {
            // Not the server's message: an error's text may repeat what the request carried.
            decodingFailed(call, method, "The answer to ${method.type} ${method.method} is an error with a 2xx status")
            return null
        }
        val value = try {
            read(body)
        } catch (e: Exception) {
            null
        }
        if (value == null) decodingFailed(call, method, "The answer to ${method.type} ${method.method} does not read as $call's result")
        return value
    }

    private fun decodingFailed(call: String, method: NetworkMethod, message: String) {
        SdkLogs.log(LogLevel.WARNING, LogCodes.DECODING_FAILED, message, shopId(),
            mapOf("call" to call, "method" to method.type, "path" to method.method))
    }

    /** The new core for [call]; otherwise the call fails with `notInitialized`, logged on the legacy core. */
    private fun newCore(call: String): CoreV2 {
        val core = core()
        if (core !is CoreV2) throw notOnThisCore(call, core)
        return core
    }

    /** What [build] makes; a wrong argument it reports is logged as `invalid_argument` and fails [call]. */
    private fun <T> refusing(call: String, reason: String?, build: () -> T): T = try {
        build()
    } catch (e: SdkException) {
        val error = e.error
        if (error is SdkError.InvalidArgument) {
            val context = mapOf("call" to call) + (reason?.let { mapOf("reason" to it) } ?: emptyMap())
            SdkLogs.log(LogLevel.WARNING, LogCodes.INVALID_ARGUMENT, "$call was refused: ${error.message}", shopId(), context)
        }
        throw e
    }

    /** The failure of [call] on an instance whose core is not the new one: [core] is the legacy one, or none. */
    private fun notOnThisCore(call: String, core: Core?): SdkException {
        if (core == null) return SdkException(SdkError.NotInitialized)
        val message = "$call runs on the new core only: start the shop with Rees46.initializeBridge"
        SdkLogs.log(LogLevel.WARNING, LogCodes.CORE_FLAG_MISMATCH, message, shopId(), mapOf("call" to call))
        return SdkException(SdkError.NotInitialized, message)
    }

    private companion object {
        const val STATUS = "status"
        const val STATUS_ERROR = "error"
    }
}
