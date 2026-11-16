package com.personalization.core

/**
 * Members for the Flutter and React Native bridges, not for apps: the bridges forward the native SDK's
 * events and errors to Dart and JavaScript without losing anything, and apps get the same things
 * through the public API (`onStateChange`, `PushCallbacks`, the old listeners).
 *
 * What it marks may change in any release, without a deprecation. A bridge opts in once per module:
 *
 * ```groovy
 * kotlinOptions {
 *     freeCompilerArgs += ['-opt-in=com.personalization.core.BridgeApi']
 * }
 * ```
 *
 * The members:
 * - `SDK.onSessionChange` — every new `sid` of the new core;
 * - `Rees46.onPushEvent` and `Rees46.onPushToken` — every push event and token the SDK sees, beside the
 *   app's `PushCallbacks`;
 * - `BridgeErrors` — the whole [SdkError] behind an old `onError(code, message)`, and listeners that
 *   report [SdkResult];
 * - `SDK.queueEvent` — a tracking event through the queue, completed once it is accepted.
 *
 * The instance's state needs nothing of its own: `SDK.onStateChange` tells the current state at once,
 * then every change.
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "For the SDK's Flutter and React Native bridges, not for apps; it may change in any release. " +
        "Opt in with @OptIn(BridgeApi::class) or -opt-in=com.personalization.core.BridgeApi.",
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.TYPEALIAS,
)
annotation class BridgeApi
