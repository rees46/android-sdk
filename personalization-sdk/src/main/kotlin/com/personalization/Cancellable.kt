package com.personalization

/**
 * Handle for a subscription that must be torn down — e.g. a [Rees46.awaitInstance] callback cancelled
 * when the view that registered it detaches, so the callback (and whatever it captures) is not leaked.
 */
fun interface Cancellable {
    fun cancel()

    companion object {
        /** A no-op handle for subscriptions that resolved synchronously and hold nothing. */
        val NOOP = Cancellable {}
    }
}
