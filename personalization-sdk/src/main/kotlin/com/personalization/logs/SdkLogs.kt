package com.personalization.logs

import android.util.Log
import com.personalization.LogLevel
import com.personalization.SDK
import com.personalization.SdkLogHandler
import com.personalization.SdkLogMessage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * The SDK's log: writes to logcat and hands each message to the app's [SdkLogHandler].
 *
 * Each shop has its own level ([register]); a message about a shop passes that level. A message that
 * is not about one shop passes the most detailed level among the shops, or [LogLevel.WARNING] while
 * there is none. Callers pass only data without personal data: method and path, codes, counts. An
 * exception is written as its class name only: its message often carries an answer's values or a URL.
 */
internal object SdkLogs {

    /** One live instance of a shop and the level it asked for. */
    private class Registration(val owner: Any, val level: LogLevel, val tag: String)

    /** The live instances of each shop, the latest last. */
    private val shops = ConcurrentHashMap<String, List<Registration>>()

    @Volatile
    var handler: SdkLogHandler? = null

    /** Where the handler runs: one background thread, so messages keep their order. */
    @Volatile
    internal var handlerExecutor: Executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "rees46-log").apply { isDaemon = true }
    }

    /**
     * Gives [shopId] the level of [owner], an instance of that shop. While several instances of a shop
     * are live, the shop logs at the most detailed of their levels, under the tag of the latest one.
     */
    fun register(shopId: String, level: LogLevel, tag: String, owner: Any = shopId) {
        shops.compute(shopId) { _, current ->
            current.orEmpty().filterNot { it.owner == owner } + Registration(owner, level, tag)
        }
    }

    /** [owner] is gone; the shop keeps a level while another of its instances is live. */
    fun unregister(shopId: String, owner: Any = shopId) {
        shops.computeIfPresent(shopId) { _, current ->
            current.filterNot { it.owner == owner }.takeIf { it.isNotEmpty() }
        }
    }

    internal fun reset() {
        shops.clear()
        handler = null
    }

    /** The level of [shopId], or for a message without a shop the most detailed level of any shop. */
    fun levelFor(shopId: String?): LogLevel {
        val registrations = shopId?.let { shops[it] } ?: shops.values.flatten()
        return registrations.minByOrNull { it.level.ordinal }?.level ?: LogLevel.WARNING
    }

    /**
     * Whether a message at [level] would be written. Without [shopId] it answers for the most detailed
     * shop, so a caller can skip building a message before it knows the shop.
     */
    fun isLoggable(level: LogLevel, shopId: String? = null): Boolean = levelFor(shopId).allows(level)

    /**
     * Writes one message. [force] writes it whatever the level, for logs the app asked for explicitly
     * (`PopupsV3.debug`).
     */
    fun log(
        level: LogLevel,
        code: String,
        message: String,
        shopId: String? = null,
        context: Map<String, Any?> = emptyMap(),
        throwable: Throwable? = null,
        force: Boolean = false,
    ) {
        if (level == LogLevel.NONE) return
        if (!force && !isLoggable(level, shopId)) return
        val tag = shopId?.let { shops[it]?.lastOrNull()?.tag } ?: SDK.TAG
        val fullContext = if (throwable == null) context else context + ("exception" to exceptionName(throwable))
        writeToLogcat(level, tag, code, message, fullContext)
        val handler = handler ?: return
        val entry = SdkLogMessage(level, code, message, shopId, fullContext)
        try {
            handlerExecutor.execute {
                try {
                    handler.onLog(entry)
                } catch (failure: Throwable) {
                    Log.w(tag, "[log_handler_failed] the app's log handler threw ${exceptionName(failure)}")
                }
            }
        } catch (rejected: Throwable) {
            Log.w(tag, "[log_handler_failed] the log handler could not be scheduled: ${exceptionName(rejected)}")
        }
    }

    /** The class of [throwable], never its message or stack trace: they may carry data. */
    fun exceptionName(throwable: Throwable): String =
        throwable.javaClass.simpleName.ifEmpty { throwable.javaClass.name }

    private fun writeToLogcat(
        level: LogLevel,
        tag: String,
        code: String,
        message: String,
        context: Map<String, Any?>,
    ) {
        val line = buildString {
            append('[').append(code).append("] ").append(message)
            if (context.isNotEmpty()) append(' ').append(context)
        }
        when (level) {
            LogLevel.DEBUG -> Log.d(tag, line)
            LogLevel.INFO -> Log.i(tag, line)
            LogLevel.WARNING -> Log.w(tag, line)
            LogLevel.ERROR -> Log.e(tag, line)
            LogLevel.NONE -> Unit
        }
    }
}

/** Stable codes of the SDK's log events. */
internal object LogCodes {
    /** A free-text message written through [SDK.debug], [SDK.warn] or [SDK.error]. */
    const val SDK_LOG = "sdk_log"

    const val REQUEST = "request"
    const val REQUEST_FAILED = "request_failed"
    const val REQUEST_BUILD_FAILED = "request_build_failed"
    const val DECODING_FAILED = "decoding_failed"
    const val CALLBACK_FAILED = "callback_failed"
    const val INVALID_ARGUMENT = "invalid_argument"
    const val INVALID_CONFIG = "invalid_config"

    const val INIT_RETRY = "init_retry"
    const val INIT_REFUSED = "init_refused"
    const val INIT_FAILED = "init_failed"

    const val PUSH_RECEIVED = "push_received"
    const val PUSH_FAILED = "push_failed"
    const val PUSH_DROPPED = "push_dropped"
    const val PUSH_TOKEN_SENT = "push_token_sent"
    const val PUSH_TOKEN_FAILED = "push_token_failed"

    const val POPUP_NOT_SHOWN = "popup_not_shown"
    const val POPUPS_V3 = "popups_v3"
    const val STORIES = "stories"
    const val STORIES_V3 = "stories_v3"
    const val UI = "ui"
}
