package com.personalization.sdk.data.repositories.network

import android.net.Uri
import com.personalization.SDK
import com.personalization.api.OnApiCallbackListener
import com.personalization.sdk.data.models.params.UserBasicParams
import com.personalization.sdk.data.utils.QueryParamsUtils.addMultipleParams
import com.personalization.sdk.domain.models.NetworkMethod
import com.personalization.sdk.domain.models.NotificationSource
import com.personalization.sdk.domain.repositories.NetworkRepository
import com.personalization.sdk.domain.repositories.NotificationRepository
import com.personalization.sdk.domain.repositories.UserSettingsRepository
import com.personalization.utils.TimeUtils
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
import java.util.Collections
import javax.inject.Inject

class NetworkRepositoryImpl @Inject constructor(
    private val userSettingsRepository: UserSettingsRepository,
    private val notificationRepository: NotificationRepository,
) : NetworkRepository {

    private lateinit var baseUrl: String

    /** How long a request may wait for the next bytes of an answer; tests shorten it. */
    internal var readTimeoutMs: Int = READ_TIMEOUT_MS

    private val queue: MutableList<Thread> = Collections.synchronizedList(ArrayList())

    override fun initialize(
        baseUrl: String
    ) {
        this.baseUrl = baseUrl
    }

    override fun post(
        method: String,
        params: JSONObject,
        listener: OnApiCallbackListener?
    ) {
        sendMethod(
            networkMethod = NetworkMethod.POST(method),
            params = params,
            listener = listener
        )
    }

    override fun postAsync(
        method: String,
        params: JSONObject,
        listener: OnApiCallbackListener?
    ) {
        sendAsync {
            sendMethod(
                networkMethod = NetworkMethod.POST(method),
                params = params,
                listener = listener
            )
        }
    }

    override fun postSecretAsync(
        method: String,
        params: JSONObject,
        listener: OnApiCallbackListener?
    ) {
        sendAsync {
            sendSecretMethod(
                networkMethod = NetworkMethod.POST(method),
                params = params,
                listener = listener
            )
        }
    }

    override fun get(
        method: String,
        params: JSONObject,
        listener: OnApiCallbackListener?
    ) {
        sendMethod(
            networkMethod = NetworkMethod.GET(method),
            params = params,
            listener = listener
        )
    }

    override fun getAsync(
        method: String,
        params: JSONObject,
        listener: OnApiCallbackListener?
    ) {
        sendAsync {
            sendMethod(
                networkMethod = NetworkMethod.GET(method),
                params = params,
                listener = listener
            )
        }
    }

    override fun getSecretAsync(
        method: String,
        params: JSONObject,
        listener: OnApiCallbackListener?
    ) {
        sendAsync {
            sendSecretMethod(
                networkMethod = NetworkMethod.GET(method),
                params = params,
                listener = listener
            )
        }
    }

    private fun sendAsync(sendFunction: () -> Unit) {
        val thread = Thread(sendFunction)
        if (userSettingsRepository.getDid()
                .isNotEmpty()
        ) {
            thread.start()
        } else {
            addTaskToQueue(thread)
        }
    }

    override fun executeQueueTasks() {
        for (thread in queue) {
            thread.start()
        }
        queue.clear()
    }

    override fun addTaskToQueue(thread: Thread) {
        queue.add(thread)
    }

    private fun sendSecretMethod(
        networkMethod: NetworkMethod,
        params: JSONObject,
        listener: OnApiCallbackListener?
    ) {
        sendMethod(
            networkMethod = networkMethod,
            params = params,
            listener = listener,
        )
    }

    private fun sendMethod(
        networkMethod: NetworkMethod,
        params: JSONObject,
        listener: OnApiCallbackListener?,
    ) {
        userSettingsRepository.updateSidLastActTime()

        val notificationSource =
            notificationRepository.getNotificationSource(TimeUtils.TWO_DAYS.inWholeMilliseconds)

        try {
            val newParams = addBasicQueryParams(
                params = params,
                notificationSource = notificationSource,
            )

            executeMethod(networkMethod, newParams, listener)
        } catch (e: JSONException) {
            SDK.error(e.message, e)
        }
    }

    private fun addBasicQueryParams(
        params: JSONObject,
        notificationSource: NotificationSource?
    ): JSONObject {
        // A request with its own `from` or `code` (a story event, a push event) keeps them: the
        // clicked push would replace that code and pair its `from` with someone else's code.
        val pushSource = notificationSource?.takeUnless {
            params.has(UserBasicParams.SOURCE_FROM) || params.has(UserBasicParams.SOURCE_CODE)
        }
        addMultipleParams(
            params = params,
            paramsToAdd = mapOf(
                UserBasicParams.SHOP_ID to userSettingsRepository.getShopId(),
                UserBasicParams.DID to userSettingsRepository.getDid(),
                UserBasicParams.SID to userSettingsRepository.getSid(),
                UserBasicParams.SEANCE to userSettingsRepository.getSid(),
                UserBasicParams.SEGMENT to resolveClientOrRepoString(
                    params = params,
                    key = UserBasicParams.SEGMENT,
                    repoValue = userSettingsRepository.getSegmentForABTesting(),
                ),
                UserBasicParams.STREAM to resolveClientOrRepoString(
                    params = params,
                    key = UserBasicParams.STREAM,
                    repoValue = userSettingsRepository.getStream(),
                ),
                UserBasicParams.SOURCE_FROM to pushSource?.type,
                UserBasicParams.SOURCE_CODE to pushSource?.id
            )
        )
        return params
    }

    /**
     * If the request body already contains a non-blank [key] (e.g. strict `trackPurchase` override), keep it;
     * otherwise use [repoValue].
     */
    private fun resolveClientOrRepoString(
        params: JSONObject,
        key: String,
        repoValue: String,
    ): String {
        if (!params.has(key) || params.isNull(key)) {
            return repoValue
        }
        val raw = params.get(key)
        val asString = when (raw) {
            is String -> raw
            else -> raw.toString()
        }
        return asString.ifBlank { repoValue }
    }

    private fun executeMethod(
        networkMethod: NetworkMethod,
        params: JSONObject,
        listener: OnApiCallbackListener?
    ) {
        val thread = Thread {
            val startTime = System.currentTimeMillis()
            val requestBodyForLog = if (networkMethod is NetworkMethod.POST) params.toString() else null
            var logUrl = baseUrl + networkMethod.method
            var responseCode = -1
            var responseBodyForLog: String? = null
            var errorForLog: String? = null
            var connection: HttpURLConnection? = null
            // The outcome is decided first and delivered once the connection is done with: a host
            // callback that throws can neither be called again nor turn into a second outcome.
            val outcome: (OnApiCallbackListener) -> Unit = try {
                val buildUri = build(networkMethod, params)
                val url = getUrl(networkMethod, buildUri)
                logUrl = url.toString()
                connection = getConnection(networkMethod, url, params)

                connection.connect()
                responseCode = connection.responseCode

                // Method and path only: the query string and the body carry the user's data.
                SDK.debug("$responseCode: ${networkMethod.type} ${networkMethod.method}")

                if (responseCode in HTTP_SUCCESS) {
                    val responseText = try {
                        connection.inputStream?.let(::readStream).orEmpty()
                    } catch (e: IOException) {
                        ""
                    }
                    responseBodyForLog = responseText
                    successOutcome(responseText)
                } else {
                    // Below 400 there is no error stream: a redirect the connection did not follow.
                    val error = connection.errorStream?.let(::readStream).orEmpty()
                    responseBodyForLog = error
                    SDK.error("$responseCode: ${networkMethod.type} ${networkMethod.method} $error")
                    errorOutcome(responseCode, error)
                }
            } catch (e: ConnectException) {
                SDK.error(e.message)
                errorForLog = e.message
                responseCode = 504
                errorOutcome(504, e.message)
            } catch (e: Exception) {
                SDK.error(e.message, e)
                errorForLog = e.message
                errorOutcome(-1, e.message)
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
                    SDK.error("NetworkLogger failed: ${e.message}", e)
                }
            }

            listener?.let { deliver(it, outcome) }
        }

        thread.start()
    }

    /** Any 2xx is a success; an empty answer or one that is not JSON succeeds with an empty object. */
    private fun successOutcome(responseText: String): (OnApiCallbackListener) -> Unit {
        val json = try {
            if (responseText.isEmpty()) null else JSONTokener(responseText).nextValue()
        } catch (e: JSONException) {
            null
        }
        return when (json) {
            is JSONObject -> { listener -> listener.onSuccess(json) }
            is JSONArray -> { listener -> listener.onSuccess(json) }
            else -> { listener -> listener.onSuccess(JSONObject()) }
        }
    }

    private fun errorOutcome(code: Int, message: String?): (OnApiCallbackListener) -> Unit =
        { listener -> listener.onError(code, message) }

    /** A callback of the host that throws is logged; it must not take down the SDK's network thread. */
    private fun deliver(listener: OnApiCallbackListener, outcome: (OnApiCallbackListener) -> Unit) {
        try {
            outcome(listener)
        } catch (e: Exception) {
            SDK.error("A request callback failed: ${e.message}", e)
        }
    }

    private fun build(
        networkMethod: NetworkMethod,
        params: JSONObject
    ): Uri {

        val builder = Uri.parse(baseUrl + networkMethod.method).buildUpon()

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
            URL(baseUrl + networkMethod.method)
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

    private fun readStream(inputStream: InputStream): String {
        var reader: BufferedReader? = null
        val response = StringBuffer()
        try {
            reader = BufferedReader(InputStreamReader(inputStream))
            var line: String?
            while ((reader.readLine().also { line = it }) != null) {
                response.append(line)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            if (reader != null) {
                try {
                    reader.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
        return response.toString()
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 5_000
        const val READ_TIMEOUT_MS = 10_000
        val HTTP_SUCCESS = 200..299
    }
}
