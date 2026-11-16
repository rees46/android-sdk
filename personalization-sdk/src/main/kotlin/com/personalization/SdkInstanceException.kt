package com.personalization

/**
 * An error in the app's code when it looks up an instance through [Rees46]: thrown at once, never
 * passed through `SdkError`. Still an [IllegalStateException], as both kinds always were.
 */
sealed class Rees46Error(message: String) : IllegalStateException(message)

/**
 * Thrown by [Rees46.instance] when the requested shop is unknown to the SDK — neither a live
 * instance nor a pending registration exists for it, so there is no config to build one from. A
 * shop that is registered-but-not-yet-initialized does not raise this: `instance` materializes it.
 * The name is deliberate: the failure is a missing registration, not an instance that was registered
 * but failed to start.
 */
class UnknownShopIdException(message: String) : Rees46Error(message)

/**
 * Thrown by [Rees46.instance] called without a `shopId` while more than one shop is registered.
 * The default instance is ambiguous — call `instance(shopId)` with an explicit id.
 */
class AmbiguousShopException(message: String) : Rees46Error(message)
