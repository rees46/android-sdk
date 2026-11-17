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
 * The path every method of a namespace (`sdk.cart`, and those after it) takes to the server: one request
 * with a result over the new core ([CoreV2.request]), its answer read into the method's model, and the
 * contract's two forms of a method — a suspending function and one that ends in a [CompletionListener].
 *
 * The namespaces run on the new core only, which the bridges start with `Rees46.initializeBridge`. On the
 * legacy core a call fails with [SdkError.NotInitialized] at once, without a request, and the log says why
 * (`core_flag_mismatch`, at warning); so it does on an instance never initialized or released.
 *
 * What a call can end with: the value; [SdkError.ShopBlocked] for a refused shop; [SdkError.Timeout] when
 * the instance was not ready within the read timeout, or the answer did not come in it; [SdkError.Network]
 * without a connection; [SdkError.Http] for an answer outside `2xx` (`serverCode` stays null: the server
 * sends no machine code yet); [SdkError.Decoding] for a `2xx` answer that does not read into the model,
 * `{"status": "error"}` among them; [SdkError.Cancelled] when `release()` ends the request. Nothing repeats
 * a request. A `402`, `403` or `404` changes nothing in the instance: it may refuse one service, not the shop.
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
     * Sends [params] to [method] for the namespace method [call] (`cart.get`, for the log) and reads the
     * `2xx` answer with [read]: it gets the answer's [JSONObject] or [org.json.JSONArray], or null for an
     * answer without a body, and returns the method's value, or null when the answer does not fit it
     * (`decoding`). A method with a read timeout of its own passes [readTimeoutMs].
     *
     * @throws SdkException with the reason the call failed.
     */
    suspend fun <T : Any> request(
        call: String,
        method: NetworkMethod,
        params: JSONObject = JSONObject(),
        readTimeoutMs: Long? = null,
        read: (Any?) -> T?,
    ): T {
        val core = core()
        if (core !is CoreV2) throw notOnThisCore(call, core)
        return when (val result = core.request(method, params, readTimeoutMs)) {
            is HttpResult.Failure -> throw SdkException(result.error)
            is HttpResult.Success -> readAnswer(call, method, result, read) ?: throw SdkException(SdkError.Decoding)
        }
    }

    /**
     * The form of a method for Java and for code outside coroutines: runs [block], its suspending form, and
     * hands [listener] its value or its error, once, on the main thread. An exception [listener] throws is
     * logged as `callback_failed` and goes no further.
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
            withContext(dispatchers().main) {
                try {
                    listener.onComplete(result)
                } catch (e: Exception) {
                    SdkLogs.log(LogLevel.ERROR, LogCodes.CALLBACK_FAILED, "The app's CompletionListener threw", shopId(), throwable = e)
                }
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

    /**
     * The new core for [call], a method that makes no request through [request] (an event of the queue, a
     * value the SDK keeps): on the legacy core or without one it fails as [request] does, the log included.
     *
     * @throws SdkException with [SdkError.NotInitialized].
     */
    fun requireNewCore(call: String): CoreV2 {
        val core = core()
        if (core !is CoreV2) throw notOnThisCore(call, core)
        return core
    }

    /**
     * Turns [call] down before it asks anything, the way the contract checks arguments: off the new core
     * with [SdkError.NotInitialized], as [request] does — so on the legacy core every call ends the same
     * way, whatever its arguments — then with [SdkError.InvalidArgument] when [problem] says what is wrong
     * with them. Returns when the call may go on to [request].
     *
     * @throws SdkException with the reason.
     */
    fun checkArguments(call: String, problem: String?) {
        val core = core()
        if (core !is CoreV2) throw notOnThisCore(call, core)
        if (problem != null) throw invalidArgument(call, problem)
    }

    /**
     * An event of the namespace method [call] (`tracking.bannerView`): its fields [params] to [path]
     * through the new core's queue, which adds the common fields and sends it. The queue gets it on the
     * caller's thread, so events keep the order of the calls. [listener] hears once, on the main thread:
     * success once the queue accepted the event, or why it did not — [SdkError.NotInitialized] off the new
     * core (logged as `core_flag_mismatch`) or after `release()`, [SdkError.InvalidArgument] for [problem],
     * [SdkError.ShopBlocked] for a refused shop. A refused event is not sent.
     */
    fun event(call: String, path: String, params: JSONObject, problem: String?, listener: CompletionListener<Unit>?) {
        val core = core()
        if (core !is CoreV2) {
            tell(listener, SdkResult.Failure(notOnThisCore(call, core).error))
            return
        }
        if (problem != null) {
            tell(listener, SdkResult.Failure(invalidArgument(call, problem).error))
            return
        }
        core.accept(path, params) { refused ->
            tell(listener, if (refused == null) SdkResult.Success(Unit) else SdkResult.Failure(refused))
        }
    }

    /** Hands [listener] its [result] once, on the main thread; one that throws is logged as `callback_failed`. */
    private fun <T> tell(listener: CompletionListener<T>?, result: SdkResult<T>) {
        if (listener == null) return
        scope.launch {
            withContext(dispatchers().main) {
                try {
                    listener.onComplete(result)
                } catch (e: Exception) {
                    SdkLogs.log(LogLevel.ERROR, LogCodes.CALLBACK_FAILED, "The app's CompletionListener threw", shopId(), throwable = e)
                }
            }
        }
    }

    /** The failure of [call] for an argument [problem] names; the log says which argument, never its value. */
    private fun invalidArgument(call: String, problem: String): SdkException {
        SdkLogs.log(LogLevel.WARNING, LogCodes.INVALID_ARGUMENT, "$call was refused: $problem", shopId(), mapOf("call" to call))
        return SdkException(SdkError.InvalidArgument(problem))
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
