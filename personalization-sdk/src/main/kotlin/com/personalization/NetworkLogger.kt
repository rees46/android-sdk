package com.personalization

/**
 * Debug hook for observing the SDK's HTTP traffic.
 *
 * The SDK performs network calls internally via `HttpURLConnection`, so a host (typically a demo
 * or QA build) cannot intercept them the way an OkHttp/axios interceptor would. Setting a logger
 * on [SDK.networkLogger] gives that single interception point: every request/response — success or
 * error — is reported here with its raw request and response bodies.
 *
 * Intended for debug builds only. The logger is process-global and called on the SDK's network
 * threads, so implementations must be thread-safe and must not block.
 */
fun interface NetworkLogger {
    /**
     * @param method HTTP method (`GET`, `POST`, …).
     * @param url full request URL (query string included for GET).
     * @param requestBody request body for POST, or null when there is none (e.g. GET).
     * @param responseCode HTTP status code, or -1 if the call failed before a response.
     * @param responseBody raw response body (success body, or error body for >= 400), or null.
     * @param durationMs wall-clock duration of the call in milliseconds.
     * @param error error message if the call threw before completing, otherwise null.
     */
    fun onHttpCall(
        method: String,
        url: String,
        requestBody: String?,
        responseCode: Int,
        responseBody: String?,
        durationMs: Long,
        error: String?
    )
}
