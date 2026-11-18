package com.personalization.core

/**
 * Why a call of the SDK failed. Every failure reaches the app as one of these, never as an exception
 * thrown into the app's code.
 *
 * Each error has a [message] for a log: English, without personal data and without the answer's body.
 */
sealed class SdkError {

    abstract val message: String

    /** The instance was not created or was released with `release()`. */
    data object NotInitialized : SdkError() {
        override val message: String get() = "The SDK instance is not initialized or was released"
    }

    /** The shop is refused: the server turned the SDK away. */
    data object ShopBlocked : SdkError() {
        override val message: String get() = "The shop is blocked"
    }

    /** A parameter of the call is wrong; [message] says which. */
    data class InvalidArgument(override val message: String) : SdkError()

    /** There is no network, or the connection was not established. */
    data object Network : SdkError() {
        override val message: String get() = "No connection to the server"
    }

    /** The answer did not come in time, or broke off in the middle. */
    data object Timeout : SdkError() {
        override val message: String get() = "The answer did not come in time"
    }

    /**
     * The server answered outside `2xx` with [code]. [serverCode] is the machine-readable code of the
     * answer, once the server sends one.
     */
    data class Http(val code: Int, val serverCode: String? = null) : SdkError() {
        override val message: String get() = "The server answered $code"
    }

    /** A `2xx` answer that does not parse. */
    data object Decoding : SdkError() {
        override val message: String get() = "The answer could not be read"
    }

    /** The request was cancelled: the instance was released, or the app cancelled the call. */
    data object Cancelled : SdkError() {
        override val message: String get() = "The request was cancelled"
    }
}

/**
 * An [SdkError] as an exception, for the suspending methods. Its message is the error's, or one that says
 * more about this failure: a method of the new core called on the legacy one says so.
 */
class SdkException internal constructor(val error: SdkError, message: String) : Exception(message) {
    constructor(error: SdkError) : this(error, error.message)
}
