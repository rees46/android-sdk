package com.personalization.core.internal

import com.personalization.LogLevel
import com.personalization.SDK
import com.personalization.core.SdkError
import com.personalization.logs.LogCodes
import com.personalization.logs.LogReasons
import com.personalization.logs.SdkLogs
import com.personalization.sdk.data.models.params.UserBasicParams
import com.personalization.sdk.domain.models.NetworkMethod
import com.personalization.sdk.domain.repositories.UserSettingsRepository
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Dispatcher
import okhttp3.EventListener
import okhttp3.Interceptor
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/** Timeouts of the new client, per request: one request's timeout never changes another's. */
internal data class CoreTimeouts(
    val connectMs: Long = 5_000,
    val readMs: Long = 10_000,
    val writeMs: Long = 10_000,
    /** `/init` has its own, shorter read timeout: an instance waits for it before its first requests. */
    val initReadMs: Long = 5_000,
)

/**
 * What the process shares between the instances' clients: one connection pool and one dispatcher.
 *
 * The dispatcher lets 16 requests run at once to one host instead of OkHttp's 5: the old client had no
 * limit, and a screen of recommendation blocks should not wait in line. OkHttp still recovers from a
 * stale pooled connection by opening a new one, as `HttpURLConnection` does; that is not a retry of an
 * answered request, which the client never makes.
 */
internal object CoreHttp {
    private const val MAX_REQUESTS_PER_HOST = 16

    val shared: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dispatcher(Dispatcher().apply { maxRequestsPerHost = MAX_REQUESTS_PER_HOST })
            .build()
    }
}

/**
 * The new core's client: one per instance, over the process's [CoreHttp.shared] pool. It adds the
 * common fields ([CommonFields]), sends, and classifies the outcome:
 * - any `2xx` is a success; an empty body is an empty success; a body that is not a JSON object or
 *   array is `decoding`;
 * - an answer outside `2xx` is `http(code)`, its body kept only for the old callbacks;
 * - no answer is `network` when the request never reached the server (no network, no connection) and
 *   `timeout` once it did (the answer did not come in time or broke off midway);
 * - a request cancelled with the instance is `cancelled`.
 *
 * Without a network a request fails at once with `network`. The client never retries; the log gets the
 * method, the path and the code, never the query, a body or the answer. An answer's `Retry-After` comes
 * back with the failure, for the caller that repeats ([CoreInit]).
 *
 * `/init` carries `did`, `sid` and `seance` only when they have a value (an empty `did` is not a
 * device), and no `segment`.
 */
internal class CoreHttpClient(
    private val baseUrl: () -> String,
    private val settings: UserSettingsRepository,
    private val connectivity: ConnectivityMonitor,
    private val shopId: String?,
    shared: OkHttpClient = CoreHttp.shared,
    internal val timeouts: CoreTimeouts = CoreTimeouts(),
    private val clock: Clock = Clock.SYSTEM,
) {

    /** This instance's client; [OkHttpClient.newBuilder] keeps the shared pool and dispatcher. */
    internal val client: OkHttpClient = shared.newBuilder()
        .connectTimeout(timeouts.connectMs, TimeUnit.MILLISECONDS)
        .readTimeout(timeouts.readMs, TimeUnit.MILLISECONDS)
        .writeTimeout(timeouts.writeMs, TimeUnit.MILLISECONDS)
        .eventListenerFactory(PhaseListener)
        .addNetworkInterceptor(RetryAfterGuard)
        .build()

    internal val initClient: OkHttpClient = client.newBuilder()
        .readTimeout(timeouts.initReadMs, TimeUnit.MILLISECONDS)
        .build()

    /** The client for [method]: `/init` reads with its own timeout. */
    internal fun clientFor(method: NetworkMethod): OkHttpClient =
        if (method.method == INIT_PATH) initClient else client

    /**
     * Sends [params] (the method's own fields) to [method]; cancelling the caller cancels the request. With
     * [commonFields] false the fields are all there already (the queue stamps its events itself).
     */
    suspend fun send(method: NetworkMethod, params: JSONObject, commonFields: Boolean = true): HttpResult {
        if (!connectivity.isConnected) {
            log(LogLevel.WARNING, LogCodes.REQUEST_FAILED, "${line(method)}: no network") {
                context(method) + ("reason" to LogReasons.OFFLINE)
            }
            return HttpResult.Failure(SdkError.Network)
        }
        val phase = Phase()
        val built = try {
            build(method, params, phase, commonFields)
        } catch (e: Exception) {
            log(LogLevel.ERROR, LogCodes.REQUEST_BUILD_FAILED, "${line(method)}: the request could not be built", e) { context(method) }
            return HttpResult.Failure(SdkError.InvalidArgument("The request could not be built"))
        }
        val started = clock.nowMillis()
        val exchange = clientFor(method).newCall(built.request).exchange(phase)
        report(method, built, exchange, clock.nowMillis() - started)
        return exchange.result
    }

    private class Built(val request: Request, val body: String?)

    private fun build(method: NetworkMethod, params: JSONObject, phase: Phase, commonFields: Boolean): Built {
        val fields = if (commonFields) CommonFields.addTo(params, settings) else params
        if (method.method == INIT_PATH) {
            INIT_OPTIONAL_FIELDS.forEach { key -> if (fields.optString(key).isEmpty()) fields.remove(key) }
            // `/init` does not read the A/B group (core/init.md): it is the events' field.
            fields.remove(UserBasicParams.SEGMENT)
        }
        val url = (baseUrl() + method.method).toHttpUrl()
        val builder = Request.Builder()
            .header(USER_AGENT, SDK.userAgent())
            .tag(Phase::class.java, phase)
        return when (method) {
            is NetworkMethod.GET -> {
                val query = url.newBuilder()
                fields.keys().forEach { key -> query.addQueryParameter(key, fields.getString(key)) }
                Built(builder.url(query.build()).get().build(), null)
            }
            is NetworkMethod.POST -> {
                val body = fields.toString()
                Built(builder.url(url).post(body.toRequestBody(JSON)).build(), body)
            }
        }
    }

    /** The outcome of one exchange; the status and the texts are for the app's [com.personalization.NetworkLogger]. */
    private class Exchange(
        val result: HttpResult,
        val status: Int = NO_STATUS,
        val responseText: String? = null,
        val failure: IOException? = null,
    )

    private suspend fun Call.exchange(phase: Phase): Exchange = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { this@exchange.cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resume(Exchange(HttpResult.Failure(noAnswer(call, phase)), failure = e))
            }

            override fun onResponse(call: Call, response: Response) {
                val exchange = try {
                    response.use { read(it) }
                } catch (e: IOException) {
                    // The status came, the body broke off: no answer, not a success with part of a body.
                    Exchange(HttpResult.Failure(noAnswer(call, phase)), response.code, failure = e)
                } catch (e: Exception) {
                    Exchange(HttpResult.Failure(SdkError.Decoding), response.code)
                }
                continuation.resume(exchange)
            }
        })
    }

    private fun read(response: Response): Exchange {
        val status = response.code
        if (status !in HTTP_SUCCESS) {
            // The status is the answer: a body that fails to arrive does not replace it.
            val errorBody = try {
                response.body?.string()
            } catch (e: IOException) {
                null
            }
            val failure = HttpResult.Failure(SdkError.Http(status), errorBody, retryAfterMs(response))
            return Exchange(failure, status, errorBody)
        }
        // OkHttp gives every answer it hands over a body; without one the answer is empty.
        val text = response.body?.string().orEmpty()
        if (text.isBlank()) return Exchange(HttpResult.Success(JSONObject()), status, text)
        val json = try {
            JSONTokener(text).nextValue()
        } catch (e: JSONException) {
            null
        }
        val result = if (json is JSONObject || json is JSONArray) HttpResult.Success(json) else HttpResult.Failure(SdkError.Decoding)
        return Exchange(result, status, text)
    }

    /**
     * `Retry-After` in seconds or as an HTTP date, as milliseconds from now; null without one or when it
     * does not parse. Read from the copy [RetryAfterGuard] keeps. Whatever the server writes, nothing
     * throws: a number of seconds may be fractional, negative or `NaN` (`0`: [Backoff] waits a second)
     * or too large for milliseconds or `inf` ([Long.MAX_VALUE]: [Backoff] waits five minutes); a date
     * already past is `0`.
     */
    private fun retryAfterMs(response: Response): Long? {
        val value = (response.header(RetryAfterGuard.COPY) ?: response.header(RETRY_AFTER))?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (value.all { it in '0'..'9' }) {
            val seconds = value.toLongOrNull() ?: return Long.MAX_VALUE
            return if (seconds > Long.MAX_VALUE / 1_000) Long.MAX_VALUE else seconds * 1_000
        }
        value.toDoubleOrNull()?.let { seconds ->
            return when {
                seconds.isNaN() || seconds <= 0 -> 0
                seconds >= Long.MAX_VALUE / 1_000.0 -> Long.MAX_VALUE
                else -> (seconds * 1_000).toLong()
            }
        }
        val date = try {
            okhttp3.Headers.headersOf(RETRY_AFTER, value).getDate(RETRY_AFTER)
        } catch (e: IllegalArgumentException) {
            null
        } ?: return null
        return (date.time - clock.nowMillis()).coerceAtLeast(0)
    }

    /** No answer: cancelled with the instance, `timeout` once the request reached the server, `network` before. */
    private fun noAnswer(call: Call, phase: Phase): SdkError = when {
        call.isCanceled() -> SdkError.Cancelled
        phase.requestSent -> SdkError.Timeout
        else -> SdkError.Network
    }

    /** Writes the outcome to the log by method, path and code, and hands the exchange to the app's network logger. */
    private fun report(method: NetworkMethod, built: Built, exchange: Exchange, durationMs: Long) {
        val status = exchange.status.takeIf { it != NO_STATUS }
        when (val result = exchange.result) {
            is HttpResult.Success ->
                log(LogLevel.DEBUG, LogCodes.REQUEST, "$status: ${line(method)}") { context(method, status) }
            is HttpResult.Failure -> when (val error = result.error) {
                is SdkError.Http ->
                    log(LogCodes.failureLevel(error.code), LogCodes.REQUEST_FAILED, "${error.code}: ${line(method)}") { context(method, status) }
                SdkError.Decoding -> {
                    log(LogLevel.DEBUG, LogCodes.REQUEST, "$status: ${line(method)}") { context(method, status) }
                    log(LogLevel.WARNING, LogCodes.DECODING_FAILED, "The answer to ${line(method)} did not parse") { context(method, status) }
                }
                SdkError.Network, SdkError.Timeout -> {
                    val name = if (error == SdkError.Timeout) ERROR_TIMEOUT else ERROR_NETWORK
                    log(LogLevel.WARNING, LogCodes.REQUEST_FAILED, "${line(method)}: no answer", exchange.failure) {
                        context(method, status, name)
                    }
                }
                SdkError.Cancelled ->
                    log(LogLevel.DEBUG, LogCodes.REQUEST_CANCELLED, "${line(method)}: cancelled") { context(method) }
                else -> Unit
            }
        }
        val networkLogger = SDK.networkLogger ?: return
        try {
            networkLogger.onHttpCall(
                method = method.type,
                url = built.request.url.toString(),
                requestBody = built.body,
                responseCode = exchange.status,
                responseBody = exchange.responseText,
                durationMs = durationMs,
                error = exchange.failure?.message
            )
        } catch (e: Exception) {
            log(LogLevel.ERROR, LogCodes.CALLBACK_FAILED, "The app's NetworkLogger threw", e) { context(method) }
        }
    }

    /** Method and path only: the query and the body carry the user's data. */
    private fun line(method: NetworkMethod): String = "${method.type} ${method.method}"

    private fun context(method: NetworkMethod, status: Int? = null, error: String? = null): Map<String, Any?> =
        buildMap {
            put("method", method.type)
            put("path", method.method)
            status?.let { put("status", it) }
            error?.let { put("error", it) }
        }

    /** The level is checked before the context is built. */
    private inline fun log(
        level: LogLevel,
        code: String,
        message: String,
        throwable: Throwable? = null,
        context: () -> Map<String, Any?>,
    ) {
        if (!SdkLogs.isLoggable(level, shopId)) return
        SdkLogs.log(level, code, message, shopId, context(), throwable)
    }

    internal companion object {
        const val INIT_PATH = "init"
        private const val USER_AGENT = "User-Agent"
        private const val RETRY_AFTER = "Retry-After"

        /** Fields `/init` leaves out while they are empty. */
        private val INIT_OPTIONAL_FIELDS = listOf(UserBasicParams.DID, UserBasicParams.SID, UserBasicParams.SEANCE)
        private const val NO_STATUS = -1
        private const val ERROR_NETWORK = "network"
        private const val ERROR_TIMEOUT = "timeout"
        private val HTTP_SUCCESS = 200..299
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

/**
 * Keeps OkHttp from repeating an answered request by itself. OkHttp sends a `408` again at once (unless
 * its `Retry-After` is positive) and a `503` whose `Retry-After` is `0`; it also parses that header as an
 * `Int` and throws on a larger number, on its own thread, which would end the app's process. For those
 * two codes the header is moved to [COPY] (empty without one), where the client reads it, and
 * `Retry-After: 1` takes its place: OkHttp hands the answer over as it is, and the core decides about
 * repeats (decision 0002). A header OkHttp would refuse to build (text outside ASCII) is copied as it is,
 * and dropped if even that fails: the guard itself never throws.
 */
internal object RetryAfterGuard : Interceptor {
    const val COPY = "X-Sdk-Retry-After"
    private const val RETRY_AFTER = "Retry-After"
    private val CODES = setOf(408, 503)

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.code !in CODES) return response
        // Empty when the server sent none: the client then reads no Retry-After, not this "1".
        val original = response.header(RETRY_AFTER).orEmpty()
        val headers = response.headers.newBuilder().removeAll(COPY).removeAll(RETRY_AFTER)
        try {
            headers.addUnsafeNonAscii(COPY, original)
        } catch (e: IllegalArgumentException) {
            // Not even a header OkHttp keeps: the answer goes without its Retry-After.
        }
        headers.add(RETRY_AFTER, "1")
        return response.newBuilder().headers(headers.build()).build()
    }
}

/** Whether the request reached the server: a failure before it is `network`, after it `timeout`. */
internal class Phase {
    @Volatile
    var requestSent: Boolean = false
}

/**
 * Follows each request of the core through its [Phase], found in the request's tags. A new connection
 * attempt (OkHttp recovering from a stale pooled connection) starts the phase over.
 */
internal object PhaseListener : EventListener.Factory {
    override fun create(call: Call): EventListener {
        val phase = call.request().tag(Phase::class.java) ?: return EventListener.NONE
        return object : EventListener() {
            override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
                phase.requestSent = false
            }

            override fun requestHeadersStart(call: Call) {
                phase.requestSent = true
            }
        }
    }
}
