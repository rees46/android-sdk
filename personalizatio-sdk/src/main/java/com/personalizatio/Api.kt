package com.personalizatio

import android.net.Uri
import org.json.JSONArray
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
import java.util.Locale

class Api private constructor(private val url: String) {

    abstract class OnApiCallbackListener {
        open fun onSuccess(response: JSONObject?) {}
        fun onSuccess(response: JSONArray) {}
        open fun onError(code: Int, msg: String?) {}
    }

    companion object {
        private var instance: Api? = null

        fun initialize(url: String) {
            instance = Api(url)
        }

        /**
         * @param method   api
         * @param params   request
         * @param listener callback
         */
        fun send(request_type: String, method: String, params: JSONObject, listener: OnApiCallbackListener?) {
            val thread = Thread {
                try {
                    val urlString = instance?.url ?: ""
                    if (urlString.isEmpty()) return@Thread

                    val builder = Uri.parse(urlString + method).buildUpon()
                    val it = params.keys()
                    while (it.hasNext()) {
                        val key = it.next()
                        builder.appendQueryParameter(key, params.getString(key))
                    }
                    val url = if (request_type.uppercase(Locale.getDefault()) == "POST") {
                        URL(urlString + method)
                    } else {
                        URL(builder.build().toString())
                    }

                    val conn = url.openConnection() as HttpURLConnection
                    conn.setRequestProperty("User-Agent", SDK.userAgent())
                    conn.requestMethod = request_type.uppercase(Locale.getDefault())
                    conn.connectTimeout = 5000

                    if (request_type.uppercase(Locale.getDefault()) == "POST") {
                        conn.setRequestProperty("Content-Type", "application/json")
                        conn.doOutput = true
                        conn.doInput = true
                        val os = BufferedWriter(OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8))
                        os.write(params.toString())
                        os.flush()
                        os.close()
                    }

                    conn.connect()

                    if (request_type.uppercase(Locale.getDefault()) == "POST") {
                        SDK.debug(conn.responseCode.toString() + ": " + request_type.uppercase(Locale.getDefault())
                                    + " " + url + " with body: " + params)
                    } else {
                        SDK.debug(conn.responseCode.toString() + ": " + request_type.uppercase(Locale.getDefault())
                                + " " + builder.build().toString())
                    }

                    if (listener != null && conn.responseCode == HttpURLConnection.HTTP_OK) {
                        val json = JSONTokener(readStream(conn.inputStream)).nextValue()
                        if (json is JSONObject) {
                            listener.onSuccess(json)
                        } else if (json is JSONArray) {
                            listener.onSuccess(json)
                        }
                    }

                    if (conn.responseCode >= 400) {
                        val error = readStream(conn.errorStream)
                        SDK.error(error)
                        listener?.onError(conn.responseCode, error)
                    }

                    conn.disconnect()
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
}
