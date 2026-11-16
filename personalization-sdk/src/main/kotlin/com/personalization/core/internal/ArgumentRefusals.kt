package com.personalization.core.internal

import com.personalization.api.OnApiCallbackListener
import com.personalization.core.SdkError
import com.personalization.logs.SdkLogs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * A call the SDK refuses before anything goes — an argument it cannot take — reported to the old listener
 * as the core reports its failures: `onError(code, message)` with [SdkError.InvalidArgument] and the
 * message behind it ([CurrentError]), so a bridge hands on `invalidArgument` with its text and not a
 * network error made from the code.
 *
 * The new core answers off the caller's thread, once, as it answers every failure; an exception the
 * listener throws is logged. The legacy core answers on the caller's thread, as it always did.
 */
internal object ArgumentRefusals {

    private val scope = CoroutineScope(SupervisorJob() + CoreDispatchers.DEFAULT.io)

    private val delivery = LegacyDelivery { level, code, message, throwable -> SdkLogs.log(level, code, message, throwable = throwable) }

    /**
     * Tells [listener] `onError([code], [message])`; [offThread] on the new core. The error behind it is
     * `invalidArgument` with [message], or a general text when there is none.
     */
    fun refuse(listener: OnApiCallbackListener?, code: Int, message: String?, offThread: Boolean) {
        listener ?: return
        val error = SdkError.InvalidArgument(message ?: DEFAULT_MESSAGE)
        if (offThread) {
            scope.launch { delivery.failure(listener, code, message, error) }
        } else {
            CurrentError.with(error) { listener.onError(code, message) }
        }
    }

    private const val DEFAULT_MESSAGE = "The call's arguments are not valid"
}
