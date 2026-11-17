package com.personalization.logs

import android.util.Log
import com.personalization.LogLevel
import com.personalization.SDK
import com.personalization.SdkLogHandler
import com.personalization.SdkLogMessage
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

    /**
     * The live instances of each shop, the latest last: an immutable snapshot that writers replace
     * under [lock] and readers take without it. (`ConcurrentHashMap.compute` would need Android 7.0.)
     */
    @Volatile
    private var shops: Map<String, List<Registration>> = emptyMap()
    private val lock = Any()

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
        synchronized(lock) {
            val registrations = shops[shopId].orEmpty().filterNot { it.owner == owner } + Registration(owner, level, tag)
            shops = shops + (shopId to registrations)
        }
    }

    /** [owner] is gone; the shop keeps a level while another of its instances is live. */
    fun unregister(shopId: String, owner: Any = shopId) {
        synchronized(lock) {
            val remaining = shops[shopId]?.filterNot { it.owner == owner } ?: return
            shops = if (remaining.isEmpty()) shops - shopId else shops + (shopId to remaining)
        }
    }

    internal fun reset() {
        synchronized(lock) { shops = emptyMap() }
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

/**
 * Stable codes of the SDK's log events: the table "Коды" of the core's logging spec, the same on Android
 * and iOS. A code is used only at its level in that table; a new code goes into the table first.
 */
internal object LogCodes {
    /** A request got an answer (`debug`); context: method, path, status. */
    const val REQUEST = "request"

    /**
     * An answer outside `2xx` or none (`error` on `4xx`, otherwise `warning`); context: method, path, status.
     * A request refused at once for want of a network has [LogReasons.OFFLINE].
     */
    const val REQUEST_FAILED = "request_failed"
    const val REQUEST_BUILD_FAILED = "request_build_failed"

    /** A request cancelled by `release()` (`debug`); context: method, path. */
    const val REQUEST_CANCELLED = "request_cancelled"

    const val INVALID_ARGUMENT = "invalid_argument"
    const val INVALID_CONFIG = "invalid_config"
    const val DECODING_FAILED = "decoding_failed"
    const val CALLBACK_FAILED = "callback_failed"

    const val INIT_SUCCEEDED = "init_succeeded"
    const val INIT_SAVED_ANSWER = "init_saved_answer"
    const val INIT_RETRY = "init_retry"

    /** `/init` failed (`error` on `4xx`, otherwise `warning`). */
    const val INIT_FAILED = "init_failed"

    /** `/init` deliberately not sent (`info`), as on a Google Play pre-launch report device; context: reason. */
    const val INIT_SKIPPED = "init_skipped"

    /** The shop refused and the core is in the `refused` state; the legacy path has no such state. */
    const val INIT_REFUSED = "init_refused"
    const val DEVICE_ID_RESET = "device_id_reset"
    const val STORAGE_FAILED = "storage_failed"
    const val QUEUE_DROPPED = "queue_dropped"

    /** A push handed to its shop (`debug`); the steps before that go under [PUSH]. */
    const val PUSH_RECEIVED = "push_received"

    /**
     * A push not handled; context: reason. `debug` for one that is not the SDK's ([LogReasons.NOT_SDK_PUSH]),
     * `warning` for one no shop takes ([LogReasons.UNKNOWN_SHOP], [LogReasons.AMBIGUOUS_SHOP]).
     */
    const val PUSH_DROPPED = "push_dropped"
    const val PUSH_FAILED = "push_failed"

    /** A push token came from FCM or HMS (`debug`), without the token itself. */
    const val PUSH_TOKEN_RECEIVED = "push_token_received"

    const val PUSH_PERMISSION = "push_permission"
    const val PUSH_PERMISSION_FAILED = "push_permission_failed"
    const val PUSH_TOKEN_SENT = "push_token_sent"
    const val PUSH_TOKEN_SKIPPED = "push_token_skipped"
    const val PUSH_TOKEN_FAILED = "push_token_failed"
    const val ADVERTISING_ID_SENT = "advertising_id_sent"
    const val ADVERTISING_ID_FAILED = "advertising_id_failed"

    /**
     * A popup not shown; context: reason. `debug` when the app kept it back ([LogReasons.APP_DECLINED],
     * [LogReasons.AUTO_PRESENTATION_OFF]), otherwise `warning`.
     */
    const val POPUP_NOT_SHOWN = "popup_not_shown"
    const val CORE_FLAG_MISMATCH = "core_flag_mismatch"

    /** Lines of the older code without an event of their own go under the code of their area. */
    const val SDK_LOG = "sdk_log"
    const val UI = "ui"
    const val STORIES = "stories"
    const val STORIES_V3 = "stories_v3"
    const val POPUPS_V3 = "popups_v3"
    const val PUSH = "push"

    /** The level of a failed request or `/init` by its HTTP code: `error` on `4xx`, otherwise `warning`. */
    fun failureLevel(status: Int?): LogLevel =
        if (status != null && status in 400..499) LogLevel.ERROR else LogLevel.WARNING
}

/** Values of `context.reason` that the logging spec names. */
internal object LogReasons {
    const val OFFLINE = "offline"
    const val NOT_SDK_PUSH = "not_sdk_push"
    const val UNKNOWN_SHOP = "unknown_shop"
    const val AMBIGUOUS_SHOP = "ambiguous_shop"
    const val APP_DECLINED = "app_declined"
    const val AUTO_PRESENTATION_OFF = "auto_presentation_off"
    const val DELETE_USER_CREDENTIALS = "delete_user_credentials"
    const val PRE_LAUNCH_REPORT = "pre_launch_report"
    const val NEED_REINITIALIZATION = "need_reinitialization"
    const val ALREADY_INITIALIZED = "already_initialized"

    /** `Rees46Config.deviceId` not taken because the shop has a `did` of its own (`invalid_config`, once per shop). */
    const val DEVICE_ID_KEPT = "device_id_kept"

    /** The A/B group a bridge brought to `Rees46.initializeBridge` not taken: the device has its own (`invalid_config`). */
    const val SEGMENT_KEPT = "segment_kept"

    /** The A/B group a bridge brought is none the SDK gives, `A` or `B` (`invalid_config`). */
    const val SEGMENT_INVALID = "segment_invalid"

    /**
     * `coreV2 = true` from an app built against the releases that had `Rees46Config.coreV2`: the new core is the
     * bridges' (`invalid_config`).
     */
    const val BRIDGE_ONLY = "bridge_only"
    const val NOT_READY = "not_ready"

    /** A shop brought up only for a push has no `did`: its event cannot go (`push_dropped`). */
    const val NO_DID = "no_did"

    // Why a push token was not sent (`push_token_skipped`).
    /** This shop sent the same token less than a week ago. */
    const val SENT_RECENTLY = "sent_recently"

    /** The instance has no `did` yet: the token goes after the `/init` that gives one. */
    const val NO_DEVICE_ID = "no_device_id"

    /** A token from FCM or HMS while the shop does not send tokens by itself (`autoSendPushToken = false`). */
    const val AUTO_SEND_OFF = "auto_send_off"

    /** The instance was brought up only for a push: the shop's own instance sends the token. */
    const val PUSH_ONLY = "push_only"

    // Why the queue dropped events (`queue_dropped`).
    /** Older than the queue keeps an event (7 days). */
    const val EXPIRED = "expired"

    /** The oldest event made room: the queue holds at most 1000. */
    const val OVERFLOW = "overflow"

    /** Ten answers to repeat in a row. */
    const val ATTEMPTS = "attempts"

    /** An answer that says the event itself is wrong (`400`, `401`, `405`, `422`, another `4xx`). */
    const val REJECTED = "rejected"

    /** A `402`, `403` or `404` once more after `/init` found the shop fine: the service is refused, not the shop. */
    const val SERVICE_REFUSED = "service_refused"

    /** The shop refused the SDK: its queue is cleared. */
    const val REFUSED = "refused"

    // Why a popup of an event's answer was not shown (`popup_not_shown`, decision 2).
    /** The event went more than 10 s after it was made. */
    const val LATE_EVENT = "late_event"

    /** The app was in the background when the answer came. */
    const val APP_IN_BACKGROUND = "app_in_background"
}
