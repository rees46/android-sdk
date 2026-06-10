package com.personalization.demo.httplogger

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * One captured HTTP call (request + response/error), mirroring the React Native demo's
 * `HttpLogEntry`. Built from the SDK's [com.personalization.NetworkLogger] hook.
 */
data class HttpLogEntry(
    val id: Long,
    val timestamp: Long,
    val method: String,
    val url: String,
    val requestBody: String?,
    val responseCode: Int,
    val responseBody: String?,
    val durationMs: Long,
    val error: String?
) {
    val isSuccess: Boolean get() = error == null && responseCode in 200..399
    val isError: Boolean get() = error != null || responseCode >= 400

    /** Short single-line header for the collapsed row. */
    fun headerLine(): String {
        val status = when {
            error != null -> "ERR"
            responseCode > 0 -> responseCode.toString()
            else -> "—"
        }
        return "$status  $method  ${shortUrl()}  ·  ${durationMs}ms"
    }

    /** Full, copy-friendly text of the whole call. */
    fun fullText(): String = buildString {
        appendLine("${timeFormat.format(Date(timestamp))}  ($durationMs ms)")
        appendLine("$method $url")
        appendLine("Status: ${if (responseCode > 0) responseCode.toString() else "—"}")
        requestBody?.takeIf { it.isNotBlank() }?.let {
            appendLine()
            appendLine("Request body:")
            appendLine(pretty(it))
        }
        responseBody?.takeIf { it.isNotBlank() }?.let {
            appendLine()
            appendLine("Response body:")
            appendLine(pretty(it))
        }
        error?.let {
            appendLine()
            appendLine("Error: $it")
        }
    }.trimEnd()

    private fun shortUrl(): String {
        val path = url.substringAfter("://").substringAfter('/', url)
        return path.substringBefore('?').let { if (it.length > 40) it.take(40) + "…" else it }
    }

    companion object {
        private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

        /** Pretty-prints a JSON request/response body; returns the raw string if it isn't JSON. */
        fun pretty(raw: String): String = try {
            when (val json = JSONTokener(raw).nextValue()) {
                is JSONObject -> json.toString(2)
                is JSONArray -> json.toString(2)
                else -> raw
            }
        } catch (e: Exception) {
            raw
        }
    }
}
