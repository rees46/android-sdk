package com.personalization.core.internal

import com.personalization.core.SdkError

/**
 * What an old `OnApiCallbackListener.onError(code, msg)` receives, and the [error] behind it when the
 * new core knows it: the bridges read it while the callback runs (`BridgeErrors.currentError`).
 */
internal data class LegacyError(val code: Int, val message: String?, val error: SdkError? = null)

/**
 * The one mapping from [SdkError] to the `onError(Int, String?)` of the old methods, so they keep the
 * codes apps know: the HTTP code for an answer outside `2xx`, `402` for a blocked shop, `-1` for a
 * failure without an answer.
 */
internal object LegacyErrors {

    /** The code of a failure without an answer. */
    const val NO_ANSWER = -1

    /** The code of a blocked shop: the server's own answer to a blocked shop. */
    const val SHOP_BLOCKED = 402

    /**
     * [errorBody] is the body of an answer outside `2xx`: the old callbacks got it as the message, and
     * apps may show the server's text from it. It is never written to the log.
     */
    fun of(error: SdkError, errorBody: String? = null): LegacyError = when (error) {
        is SdkError.Http -> LegacyError(error.code, errorBody ?: error.message, error)
        SdkError.ShopBlocked -> LegacyError(SHOP_BLOCKED, error.message, error)
        SdkError.Network,
        SdkError.Timeout,
        SdkError.Decoding,
        SdkError.NotInitialized,
        SdkError.Cancelled,
        is SdkError.InvalidArgument -> LegacyError(NO_ANSWER, error.message, error)
    }
}
