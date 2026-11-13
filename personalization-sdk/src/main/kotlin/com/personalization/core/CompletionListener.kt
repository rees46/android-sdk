package com.personalization.core

/**
 * The end of a call for Java and for code outside coroutines: the second form of a suspending method,
 * with this listener as its last, required parameter. Called once, on the main thread; an exception it
 * throws is logged as `callback_failed` and goes no further.
 */
fun interface CompletionListener<T> {
    fun onComplete(result: SdkResult<T>)
}
