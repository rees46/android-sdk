package com.personalizatio.api

import android.net.Uri
import com.personalizatio.SDK
import com.personalizatio.notifications.Source
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

class ApiImpl(
    private val baseUrl: String
): Api {

    /**
     * Прямое выполенение запроса
     */
    override fun send(apiMethod: ApiMethod, params: JSONObject, listener: OnApiCallbackListener?,
             shopId: String, did: String?, seance: String?, segment: String, stream: String, source: Source
    ) {
        try {
            params.put("shop_id", shopId)
            if (did != null) {
                params.put("did", did)
            }
            if (seance != null) {
                params.put("seance", seance)
                params.put("sid", seance)
            }
            params.put("segment", segment)
            params.put("stream", stream)

            val sourceJson = source.getJsonObject(sourceTimeDuration)
            if (sourceJson != null) {
                params.put("source", sourceJson)
            }

            send(apiMethod, params, listener)
        } catch (e: JSONException) {
            SDK.error(e.message, e)
        }
    }

    private fun send(apiMethod: ApiMethod, params: JSONObject, listener: OnApiCallbackListener?) {
        val thread = Thread {
            try {
                val buildUri = build(apiMethod, params)
                val url = getUrl(apiMethod, buildUri)
                val connection = getConnection(apiMethod, url, params)

                connection.connect()

                if (apiMethod is ApiMethod.POST) {
                    SDK.debug(connection.responseCode.toString() + ": " + apiMethod.type + " " + url + " with body: " + params)
                } else {
                    SDK.debug(connection.responseCode.toString() + ": " + apiMethod.type + " " + buildUri.toString())
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

    private fun build(apiMethod: ApiMethod, params: JSONObject): Uri {
        val builder = Uri.parse(baseUrl + apiMethod.method).buildUpon()

        val it = params.keys()
        while (it.hasNext()) {
            val key = it.next()
            builder.appendQueryParameter(key, params.getString(key))
        }

        return builder.build()
    }

    private fun getUrl(apiMethod: ApiMethod, buildUri: Uri) : URL {
        return if (apiMethod is ApiMethod.POST) {
            URL(baseUrl + apiMethod.method)
        } else {
            URL(buildUri.toString())
        }
    }

    private fun getConnection(apiMethod: ApiMethod, url: URL, params: JSONObject) : HttpURLConnection {
        val connection = url.openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", SDK.userAgent())
        connection.requestMethod = apiMethod.type
        connection.connectTimeout = 5000

        if (apiMethod is ApiMethod.POST) {
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
    }
}