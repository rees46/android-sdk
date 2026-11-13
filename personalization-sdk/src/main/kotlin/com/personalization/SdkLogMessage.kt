package com.personalization

/**
 * One message of the SDK's log, as [Rees46.setLogHandler] receives it.
 *
 * @property level how serious the message is.
 * @property code a stable code of the event, such as `init_retry` or `decoding_failed`: match on it,
 * not on [message].
 * @property message an English text without personal data.
 * @property shopId the shop the message is about, or `null` when it is not about one shop.
 * @property context details without personal data: the request path, the HTTP code, the attempt.
 */
data class SdkLogMessage(
    val level: LogLevel,
    val code: String,
    val message: String,
    val shopId: String?,
    val context: Map<String, Any?> = emptyMap(),
)

/**
 * Receives the SDK's log messages that pass the shop's [Rees46Config.logLevel], for example to forward
 * them to the app's crash reporter. Called on a background thread; an exception it throws is caught.
 */
fun interface SdkLogHandler {
    fun onLog(message: SdkLogMessage)
}
