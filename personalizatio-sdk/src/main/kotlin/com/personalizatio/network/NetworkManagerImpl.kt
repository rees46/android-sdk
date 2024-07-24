package com.personalizatio.network

import android.net.Uri
import com.personalizatio.RegisterManager
import com.personalizatio.SDK
import com.personalizatio.api.OnApiCallbackListener
import com.personalizatio.api.managers.NetworkManager
import com.personalizatio.domain.usecases.notification.GetNotificationSourceUseCase
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

internal class NetworkManagerImpl @Inject constructor(
    private val registerManager: RegisterManager,
    private val getNotificationSourceUseCase: GetNotificationSourceUseCase
): NetworkManager {

    private lateinit var baseUrl: String
    private lateinit var shopId: String
    private var seance: String? = null
    private lateinit var segment: String
    private lateinit var stream: String

    override fun initialize(
        baseUrl: String,
        shopId: String,
        seance: String?,
        segment: String,
        stream: String
    ) {
        this.baseUrl = baseUrl
        this.shopId = shopId
        this.seance = seance
        this.segment = segment
        this.stream = stream
    }

    private val queue: MutableList<Thread> = Collections.synchronizedList(ArrayList())

    override fun post(method: String, params: JSONObject, listener: OnApiCallbackListener?) {
        send(NetworkMethod.POST(method), params, listener)
    }

    override fun postAsync(method: String, params: JSONObject, listener: OnApiCallbackListener?) {
        sendAsync { send(NetworkMethod.POST(method), params, listener) }
    }

    override fun get(method: String, params: JSONObject, listener: OnApiCallbackListener?) {
        send(NetworkMethod.GET(method), params, listener)
    }

    override fun getAsync(method: String, params: JSONObject, listener: OnApiCallbackListener?) {
        sendAsync { send(NetworkMethod.GET(method), params, listener) }
    }

    private fun send(networkMethod: NetworkMethod, params: JSONObject, listener: OnApiCallbackListener?) {
        registerManager.updateSidActivity()

        sendMethod(networkMethod, params, listener)
    }

    private fun sendAsync(sendFunction: () -> Unit) {
        val thread = Thread(sendFunction)
        if (registerManager.did != null && registerManager.isInitialized) {
            thread.start()
        } else {
            queue.add(thread)
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

    private fun sendMethod(networkMethod: NetworkMethod, params: JSONObject, listener: OnApiCallbackListener?) {
        try {
            params.put(SHOP_ID_PARAMS_FIELD, shopId)
            if (registerManager.did != null) {
                params.put(DID_PARAMS_FIELD, registerManager.did)
            }
            if (seance != null) {
                params.put(SEANCE_PARAMS_FIELD, seance)
                params.put(SID_PARAMS_FIELD, seance)
            }
            params.put(SEGMENT_PARAMS_FIELD, segment)
            params.put(STREAM_PARAMS_FIELD, stream)

            val notificationSource = getNotificationSourceUseCase.execute(sourceTimeDuration)
            if (notificationSource != null) {
                val notificationObject = JSONObject()
                    .put(SOURCE_FROM_FIELD, notificationSource.type)
                    .put(SOURCE_CODE_FIELD, notificationSource.id)
                params.put(SOURCE_PARAMS_FIELD, notificationObject)
            }

            executeMethod(networkMethod, params, listener)
        } catch (e: JSONException) {
            SDK.error(e.message, e)
        }
    }

    private fun executeMethod(networkMethod: NetworkMethod, params: JSONObject, listener: OnApiCallbackListener?) {
        val thread = Thread {
            try {
                val buildUri = build(networkMethod, params)
                val url = getUrl(networkMethod, buildUri)
                val connection = getConnection(networkMethod, url, params)

                connection.connect()

                if (networkMethod is NetworkMethod.POST) {
                    SDK.debug(connection.responseCode.toString() + ": " + networkMethod.type + " " + url + " with body: " + params)
                } else {
                    SDK.debug(connection.responseCode.toString() + ": " + networkMethod.type + " " + buildUri.toString())
                }

                if (listener != null && connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val json = JSONTokener(readStream(connection.inputStream)).nextValue()
                    if (json is JSONObject) {
                        listener.onSuccess(json)
                    } else if (json is JSONArray) {
                        listener.onSuccess(json)
                    }
                }

                if (connection.responseCode >= 400) {
                    val error = readStream(connection.errorStream)
                    SDK.error(error)
                    listener?.onError(connection.responseCode, error)
                }

                connection.disconnect()
            } catch (e: ConnectException) {
                SDK.error(e.message)
                listener?.onError(504, e.message)
            } catch (e: Exception) {
                SDK.error(e.message, e)
                listener?.onError(-1, e.message)
            }
        }

        thread.start()
    }

    private fun build(networkMethod: NetworkMethod, params: JSONObject): Uri {
        val builder = Uri.parse(baseUrl + networkMethod.method).buildUpon()

        val it = params.keys()
        while (it.hasNext()) {
            val key = it.next()
            builder.appendQueryParameter(key, params.getString(key))
        }

        return builder.build()
    }

    private fun getUrl(networkMethod: NetworkMethod, buildUri: Uri) : URL {
        return if (networkMethod is NetworkMethod.POST) {
            URL(baseUrl + networkMethod.method)
        } else {
            URL(buildUri.toString())
        }
    }

    private fun getConnection(networkMethod: NetworkMethod, url: URL, params: JSONObject) : HttpURLConnection {
        val connection = url.openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", SDK.userAgent())
        connection.requestMethod = networkMethod.type
        connection.connectTimeout = 5000

        if (networkMethod is NetworkMethod.POST) {
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.doInput = true
            val os = BufferedWriter(OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8))
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

    companion object {
        private var sourceTimeDuration = 172800 * 1000 // 2 days

        private const val SHOP_ID_PARAMS_FIELD = "shop_id"
        private const val DID_PARAMS_FIELD = "did"
        private const val SEANCE_PARAMS_FIELD = "seance"
        private const val SID_PARAMS_FIELD = "sid"
        private const val SEGMENT_PARAMS_FIELD = "segment"
        private const val STREAM_PARAMS_FIELD = "stream"
        private const val SOURCE_PARAMS_FIELD = "source"
        private const val SOURCE_FROM_FIELD = "from"
        private const val SOURCE_CODE_FIELD = "code"
    }
}
