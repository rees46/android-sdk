package com.personalization

/**
 * Thrown by [Rees46.getInstance] when no SDK matches the request: either nothing has been initialized
 * or registered yet, or no instance/registration exists for the requested `shopId`.
 */
class SdkNotInitializedException(message: String) : IllegalStateException(message)

/**
 * Thrown by [Rees46.getInstance] called without a `shopId` while more than one shop is registered.
 * The default instance is ambiguous — call `getInstance(shopId)` with an explicit id.
 */
class AmbiguousSdkInstanceException(message: String) : IllegalStateException(message)
