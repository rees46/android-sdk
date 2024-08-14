package com.personalization.sdk.data.repositories.network

import android.net.Uri
import com.personalization.SDK
import com.personalization.api.OnApiCallbackListener
import com.personalization.sdk.data.repositories.user.UserSettingsDataSource
import com.personalization.sdk.domain.models.NetworkMethod
import com.personalization.sdk.domain.repositories.NetworkRepository
import com.personalization.sdk.domain.repositories.NotificationRepository
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
import javax.inject.Inject

class NetworkRepositoryImpl @Inject constructor(
    private val networkDataSource: NetworkDataSource,
    private val userDataSource: UserSettingsDataSource,
    private val notificationRepository: NotificationRepository
) : NetworkRepository {

    override fun initialize(
        baseUrl: String
    ) {
        networkDataSource.initialize(
            baseUrl = baseUrl
        )
    }

    override fun sendMethod(networkMethod: NetworkMethod, params: JSONObject, listener: OnApiCallbackListener?) {
        val sourceTimeDuration = notificationRepository.getNotificationSource(NetworkDataSource.sourceTimeDuration)

        try {
            val newParams = userDataSource.addParams(params, sourceTimeDuration)

            executeMethod(networkMethod, newParams, listener)
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
        val builder = Uri.parse(networkDataSource.baseUrl + networkMethod.method).buildUpon()

        val it = params.keys()
        while (it.hasNext()) {
            val key = it.next()
            builder.appendQueryParameter(key, params.getString(key))
        }

        return builder.build()
    }

    private fun getUrl(networkMethod: NetworkMethod, buildUri: Uri) : URL {
        return if (networkMethod is NetworkMethod.POST) {
            URL(networkDataSource.baseUrl + networkMethod.method)
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
}
