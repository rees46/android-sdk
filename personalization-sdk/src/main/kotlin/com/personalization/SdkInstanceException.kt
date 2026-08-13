package com.personalization

/**
 * Thrown by [Rees46.getInstance] when the requested shop is unknown to the SDK — neither a live
 * instance nor a pending registration exists for it, so there is no config to build one from. A
 * shop that is registered-but-not-yet-initialized does not raise this: `getInstance` materializes it.
 * The name is deliberate: the failure is a missing registration, not an instance that was registered
 * but failed to start.
 */
class UnknownShopIdException(message: String) : IllegalStateException(message)

/**
 * Thrown by [Rees46.getInstance] called without a `shopId` while more than one shop is registered.
 * The default instance is ambiguous — call `getInstance(shopId)` with an explicit id.
 */
class AmbiguousShopException(message: String) : IllegalStateException(message)
