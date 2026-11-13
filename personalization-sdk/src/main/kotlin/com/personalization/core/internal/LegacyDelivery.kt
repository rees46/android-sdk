package com.personalization.core.internal

import com.personalization.LogLevel
import com.personalization.api.OnApiCallbackListener
import com.personalization.core.SdkError
import com.personalization.logs.LogCodes
import org.json.JSONArray
import org.json.JSONObject

/**
 * Hands an answer to a listener of the old methods. A callback that throws is logged and goes no
 * further. A failed `onSuccess` ends in one `onError(-1)`: the SDK's own listeners parse the answer
 * there and hand the model to the app, so without it a parse error would leave the app waiting.
 */
internal class LegacyDelivery(
    private val log: (level: LogLevel, code: String, message: String, throwable: Throwable?) -> Unit,
) {

    /** [body] is a [JSONObject] or a [JSONArray]. */
    fun success(listener: OnApiCallbackListener, body: Any) {
        try {
            when (body) {
                is JSONArray -> listener.onSuccess(body)
                else -> listener.onSuccess(body as? JSONObject)
            }
        } catch (e: Exception) {
            log(LogLevel.ERROR, LogCodes.CALLBACK_FAILED, "onSuccess of a request threw", e)
            failure(listener, LegacyErrors.NO_ANSWER, e.message, SdkError.Decoding)
        }
    }

    /**
     * Calls `onError(code, message)`; while it runs, [error] is the thread's [CurrentError], so a bridge's
     * listener — or a listener the SDK wrapped around it and forwards from — can read the whole error.
     */
    fun failure(listener: OnApiCallbackListener, code: Int, message: String?, error: SdkError? = null) {
        try {
            CurrentError.with(error) { listener.onError(code, message) }
        } catch (e: Exception) {
            log(LogLevel.ERROR, LogCodes.CALLBACK_FAILED, "onError of a request threw", e)
        }
    }

    fun failure(listener: OnApiCallbackListener, error: LegacyError) = failure(listener, error.code, error.message, error.error)
}
