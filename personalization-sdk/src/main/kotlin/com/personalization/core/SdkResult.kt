package com.personalization.core

/** What a [CompletionListener] receives: the value of a call that succeeded, or why it failed. */
sealed interface SdkResult<out T> {

    data class Success<T>(val value: T) : SdkResult<T>

    data class Failure(val error: SdkError) : SdkResult<Nothing>
}
