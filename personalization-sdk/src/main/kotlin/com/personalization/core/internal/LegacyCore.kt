package com.personalization.core.internal

import android.net.Uri
import com.personalization.LogLevel
import com.personalization.SDK
import com.personalization.api.OnApiCallbackListener
import com.personalization.core.SdkError
import com.personalization.logs.LogCodes
import com.personalization.logs.SdkLogs
import com.personalization.sdk.domain.models.NetworkMethod
import com.personalization.sdk.domain.repositories.UserSettingsRepository
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executor

/**
 * Today's request path, unchanged: `HttpURLConnection` on a thread per request. Any `2xx` is a
 * success, an empty answer or one that is not JSON succeeds with an empty object, no connection is
 * `504` and a failure without an answer `-1`. It cannot cancel a request in flight; after [release] a
 * request fails at once with `notInitialized` (`onError(-1)`).
 */
internal class LegacyCore(
    private val settings: UserSettingsRepository,
    private val baseUrl: () -> String,
) : Core {

    override val isV2: Boolean = false

    /** How long a request may wait for the next bytes of an answer; tests shorten it. */
    @Volatile
    var readTimeoutMs: Int = READ_TIMEOUT_MS

    private val delivery = LegacyDelivery { level, code, message, throwable -> log(level, code, message, throwable = throwable) }

    @Volatile
    private var released = false

    override fun send(method: NetworkMethod, params: JSONObject, listener: OnApiCallbackListener?) {
        if (released) {
            listener?.let(::refuse)
            return
        }
        try {
            executeMethod(method, CommonFields.addTo(params, settings), listener)
        } catch (e: JSONException) {
            log(LogLevel.ERROR, LogCodes.REQUEST_BUILD_FAILED, "A request could not be built", method, throwable = e)
        }
    }

    override fun release() {
        released = true
    }

    /** Tells [listener] `notInitialized` (`onError(-1)`) without sending, off the caller's thread as every answer of this path. */
    fun refuse(listener: OnApiCallbackListener) {
        val error = LegacyErrors.of(SdkError.NotInitialized)
        threads.execute { delivery.failure(listener, error) }
    }

    /** The legacy path has no queue: the partition's credentials and the saved answer go. */
    override suspend fun deleteUserCredentials(storage: ShopStorage): Boolean = storage.deleteUserCredentials(includeQueue = false)

    private fun executeMethod(
        networkMethod: NetworkMethod,
        params: JSONObject,
        listener: OnApiCallbackListener?
    ) {
        threads.execute {
            val startTime = System.currentTimeMillis()
            val requestBodyForLog = if (networkMethod is NetworkMethod.POST) params.toString() else null
            var logUrl = ""
            var responseCode = -1
            var responseBodyForLog: String? = null
            var errorForLog: String? = null
            var connection: HttpURLConnection? = null
            // The outcome is decided first and delivered once the connection is done with, so a
            // callback that throws cannot change it.
            val outcome: Outcome = try {
                logUrl = baseUrl() + networkMethod.method
                val buildUri = build(networkMethod, params)
                val url = getUrl(networkMethod, buildUri)
                logUrl = url.toString()
                connection = getConnection(networkMethod, url, params)

                connection.connect()
                responseCode = connection.responseCode

                // Method and path only: the query string and the body carry the user's data.
                log(LogLevel.DEBUG, LogCodes.REQUEST, "Request answered", networkMethod, responseCode)

                if (responseCode in HTTP_SUCCESS) {
                    // A body the read timeout cuts short throws here and ends in onError.
                    val responseText = readStream(connection.inputStream)
                    responseBodyForLog = responseText
                    successOutcome(responseText)
                } else {
                    // Below 400 there is no error stream: a redirect the connection did not follow.
                    // The status is the answer, so a body that fails to arrive does not replace it.
                    val error = try {
                        connection.errorStream?.let(::readStream).orEmpty()
                    } catch (e: IOException) {
                        ""
                    }
                    responseBodyForLog = error
                    // Without the body: an error answer often repeats the request's data.
                    log(LogCodes.failureLevel(responseCode), LogCodes.REQUEST_FAILED, "Request failed", networkMethod, responseCode)
                    Outcome.Failure(responseCode, error)
                }
            } catch (e: ConnectException) {
                log(LogLevel.WARNING, LogCodes.REQUEST_FAILED, "No connection to the server", networkMethod, throwable = e)
                errorForLog = e.message
                responseCode = 504
                Outcome.Failure(504, e.message)
            } catch (e: Exception) {
                log(LogLevel.WARNING, LogCodes.REQUEST_FAILED, "Request ended without an answer", networkMethod, throwable = e)
                errorForLog = e.message
                Outcome.Failure(-1, e.message)
            } finally {
                connection?.disconnect()
                try {
                    SDK.networkLogger?.onHttpCall(
                        method = networkMethod.type,
                        url = logUrl,
                        requestBody = requestBodyForLog,
                        responseCode = responseCode,
                        responseBody = responseBodyForLog,
                        durationMs = System.currentTimeMillis() - startTime,
                        error = errorForLog
                    )
                } catch (e: Exception) {
                    log(LogLevel.ERROR, LogCodes.CALLBACK_FAILED, "The app's NetworkLogger threw", networkMethod, throwable = e)
                }
            }

            if (listener != null) {
                when (outcome) {
                    is Outcome.Success -> delivery.success(listener, outcome.body)
                    is Outcome.Failure -> delivery.failure(listener, outcome.code, outcome.message)
                }
            }
        }
    }

    private sealed interface Outcome {
        /** [body] is a [JSONObject] or a [JSONArray]. */
        class Success(val body: Any) : Outcome
        class Failure(val code: Int, val message: String?) : Outcome
    }

    /** Any 2xx is a success; an empty answer or one that is not JSON succeeds with an empty object. */
    private fun successOutcome(responseText: String): Outcome {
        val json = try {
            if (responseText.isEmpty()) null else JSONTokener(responseText).nextValue()
        } catch (e: JSONException) {
            null
        }
        return Outcome.Success(if (json is JSONObject || json is JSONArray) json else JSONObject())
    }

    private fun build(
        networkMethod: NetworkMethod,
        params: JSONObject
    ): Uri {

        val builder = Uri.parse(baseUrl() + networkMethod.method).buildUpon()

        val it = params.keys()
        while (it.hasNext()) {
            val key = it.next()
            builder.appendQueryParameter(key, params.getString(key))
        }

        return builder.build()
    }

    private fun getUrl(
        networkMethod: NetworkMethod,
        buildUri: Uri
    ): URL {

        return if (networkMethod is NetworkMethod.POST) {
            URL(baseUrl() + networkMethod.method)
        } else {
            URL(buildUri.toString())
        }
    }

    private fun getConnection(
        networkMethod: NetworkMethod,
        url: URL,
        params: JSONObject
    ): HttpURLConnection {
        val connection = url.openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", SDK.userAgent())
        connection.requestMethod = networkMethod.type
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = readTimeoutMs

        if (networkMethod is NetworkMethod.POST) {
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.doInput = true
            val os =
                BufferedWriter(OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8))
            os.write(params.toString())
            os.flush()
            os.close()
        }

        return connection
    }

    /** The whole answer, its lines joined; a read that fails midway throws instead of returning a part. */
    private fun readStream(inputStream: InputStream): String =
        BufferedReader(InputStreamReader(inputStream)).use { reader ->
            buildString { reader.forEachLine { append(it) } }
        }

    /**
     * Writes to the log by method and path only: the query and the body carry the user's data. The
     * level is checked first, so a line no shop writes reads no preferences and builds nothing.
     */
    private fun log(
        level: LogLevel,
        code: String,
        message: String,
        method: NetworkMethod? = null,
        status: Int? = null,
        throwable: Throwable? = null,
    ) {
        if (!SdkLogs.isLoggable(level)) return
        val context = buildMap<String, Any?> {
            method?.let { put("method", it.type); put("path", it.method) }
            status?.let { put("status", it) }
        }
        val shopId = runCatching { settings.getShopId() }.getOrNull()?.takeIf { it.isNotEmpty() }
        SdkLogs.log(level, code, message, shopId, context, throwable)
    }

    internal companion object {
        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val READ_TIMEOUT_MS = 10_000
        private val HTTP_SUCCESS = 200..299

        internal val DEFAULT_THREADS = Executor { Thread(it).start() }

        /** Runs each request and each answer of the path: a thread apiece. Tests pass one they can wait on. */
        @Volatile
        internal var threads: Executor = DEFAULT_THREADS
    }
}
