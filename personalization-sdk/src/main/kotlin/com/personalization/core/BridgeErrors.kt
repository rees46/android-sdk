package com.personalization.core

import com.personalization.api.OnApiCallbackListener
import com.personalization.core.internal.CurrentError
import org.json.JSONArray
import org.json.JSONObject

/**
 * For the bridges: the whole [SdkError] — its type, HTTP code and `serverCode` — behind an old
 * `onError(code, message)`, so a bridge hands Dart and JavaScript the error the core had, not a code.
 *
 * Every old method keeps calling `onError(code, message)`; while the SDK calls it, [currentError] is the
 * error behind it, on that thread. It reaches a bridge's listener through the listeners the SDK's managers
 * wrap around it, which forward on the same thread. On the legacy core, which has no [SdkError], it is
 * null and [errorOf] rebuilds one from the code.
 */
@BridgeApi
object BridgeErrors {

    /** The error behind the `onError` the SDK is calling on this thread; null outside one or on the legacy core. */
    @JvmStatic
    fun currentError(): SdkError? = CurrentError.get()

    /**
     * The error of an `onError(code, message)` the SDK is calling: [currentError] when the core knows it,
     * otherwise one made from [code] — [SdkError.Http] for an HTTP status, [SdkError.Network] for `-1`.
     */
    @JvmStatic
    fun errorOf(code: Int, message: String?): SdkError = currentError() ?: when (code) {
        in HTTP_STATUSES -> SdkError.Http(code)
        else -> SdkError.Network
    }

    /**
     * The error an instance in [state] gives every call: [SdkError.ShopBlocked] for a shop that refused the
     * SDK ([SdkState.REFUSED]), [SdkError.NotInitialized] before initialization and after `release()`; null
     * while calls may go. What a bridge reports for an `initialize` whose shop refused the SDK.
     */
    @JvmStatic
    fun errorOf(state: SdkState): SdkError? = when (state) {
        SdkState.REFUSED -> SdkError.ShopBlocked
        SdkState.NOT_INITIALIZED -> SdkError.NotInitialized
        SdkState.INITIALIZING, SdkState.READY, SdkState.RETRYING -> null
    }

    /**
     * An old-style listener that reports the whole result to [onResult], once, on the thread the SDK
     * answers on: [SdkResult.Success] with the answer (a [JSONObject], a [JSONArray] or null), or
     * [SdkResult.Failure] with [errorOf] the failure. Pass it to any method that takes an
     * [OnApiCallbackListener].
     */
    @JvmStatic
    fun listener(onResult: (SdkResult<Any?>) -> Unit): OnApiCallbackListener = object : OnApiCallbackListener() {
        override fun onSuccess(response: JSONObject?) = onResult(SdkResult.Success(response))

        override fun onSuccess(response: JSONArray) = onResult(SdkResult.Success(response))

        override fun onError(code: Int, msg: String?) = onResult(SdkResult.Failure(errorOf(code, msg)))
    }

    private val HTTP_STATUSES = 100..599
}
