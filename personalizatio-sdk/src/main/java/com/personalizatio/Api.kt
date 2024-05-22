package com.personalizatio

import android.net.Uri

/**
 * Created by Sergey Odintsov
 *
 * @author nixx.dj@gmail.com
 */
class Api private constructor(private val url: String?) {
    abstract class OnApiCallbackListener {
        fun onSuccess(response: JSONObject?) {}
        fun onSuccess(response: JSONArray?) {}
        fun onError(code: Int, msg: String?) {}
    }

    companion object {
        private var instance: Api? = null
        fun initialize(url: String?) {
            instance = Api(url)
        }

        /**
         * @param method   api
         * @param params   request
         * @param listener callback
         */
        fun send(
            request_type: String?,
            method: String?,
            params: JSONObject?,
            @Nullable listener: OnApiCallbackListener?
        ) {
            val thread: Thread = Thread {
                try {
                    val builder: Uri.Builder = Uri.parse(instance.url + method).buildUpon()
                    val it: Iterator<String?> = params.keys()
                    while (it.hasNext()) {
                        val key = it.next()
                        builder.appendQueryParameter(key, params.getString(key))
                    }

                    val url: URL?
                    if (request_type.toUpperCase().equals("POST")) {
                        url = URL(instance.url + method)
                    } else {
                        url = URL(builder.build().toString())
                    }

                    val conn: HttpURLConnection = url.openConnection() as HttpURLConnection
                    conn.setRequestProperty("User-Agent", SDK.userAgent())
                    conn.setRequestMethod(request_type.toUpperCase())
                    conn.setConnectTimeout(5000)

                    if (request_type.toUpperCase().equals("POST")) {
                        conn.setRequestProperty("Content-Type", "application/json")
                        conn.setDoOutput(true)
                        conn.setDoInput(true)
                        val os: BufferedWriter =
                            BufferedWriter(OutputStreamWriter(conn.getOutputStream(), StandardCharsets.UTF_8))
                        os.write(params.toString())
                        os.flush()
                        os.close()
                    }

                    conn.connect()

                    if (request_type.toUpperCase().equals("POST")) {
                        SDK.debug((conn.getResponseCode() + ": " + request_type.toUpperCase()).toString() + " " + url + " with body: " + params)
                    } else {
                        SDK.debug(
                            (conn.getResponseCode() + ": " + request_type.toUpperCase()).toString() + " " + builder.build()
                                .toString()
                        )
                    }

                    if (listener != null && conn.getResponseCode() === HttpURLConnection.HTTP_OK) {
                        val json: Object = JSONTokener(readStream(conn.getInputStream())).nextValue()
                        if (json is JSONObject) {
                            listener.onSuccess(json as JSONObject)
                        } else if (json is JSONArray) {
                            listener.onSuccess(json as JSONArray)
                        }
                    }

                    if (conn.getResponseCode() >= 400) {
                        val error = readStream(conn.getErrorStream())
                        SDK.error(error)
                        listener?.onError(conn.getResponseCode(), error)
                    }

                    conn.disconnect()
                } catch (e: ConnectException) {
                    SDK.error(e.getMessage())
                    listener?.onError(504, e.getMessage())
                } catch (e: Exception) {
                    SDK.error(e.getMessage(), e)
                    if (listener != null) {
                        listener.onError(-1, e.getMessage())
                    }
                }
            }

            thread.start()
        }

        private fun readStream(`in`: InputStream?): String? {
            var reader: BufferedReader? = null
            val response: StringBuffer = StringBuffer()
            try {
                reader = BufferedReader(InputStreamReader(`in`))
                var line: String? = ""
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
