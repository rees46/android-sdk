package com.personalization.core

/**
 * The end of a call that returns nothing, for Java and for code outside coroutines. Called once, on the
 * main thread.
 */
fun interface CompletionListener {
    /** [error] is null when the call succeeded. */
    fun onComplete(error: SdkError?)
}
