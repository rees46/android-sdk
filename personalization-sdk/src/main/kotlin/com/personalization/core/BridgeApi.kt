package com.personalization.core

/**
 * Members for the Flutter and React Native bridges, not for apps: the bridges start their shops on the
 * new core, and forward the native SDK's events and errors to Dart and JavaScript without losing
 * anything; apps get those through the public API (`onStateChange`, `PushCallbacks`, the old listeners).
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
 * - `Rees46.initializeBridge` — a shop on the new core, which apps cannot pick, with the A/B group the
 *   bridge kept for the device;
 * - `SDK.onSessionChange` — every new `sid` of the new core;
 * - `Rees46.onPushEvent` and `Rees46.onPushToken` — every push event and token the SDK sees, beside the
 *   app's `PushCallbacks`;
 * - `BridgeErrors` — the whole [SdkError] behind an old `onError(code, message)`, and listeners that
 *   report [SdkResult];
 * - `SDK.queueEvent` — a tracking event through the queue, completed once it is accepted;
 * - the namespaces of the new core, `SDK.cart` the first: each method is a suspending function with a
 *   twin that ends in a [CompletionListener], and fails with [SdkError] — [SdkError.NotInitialized] on the
 *   legacy core, where they do not run;
 * - `SDK.reviews` — NPS, CSAT and CES ratings and their dictionaries on `Rees46Config.appDomain`, and the
 *   reviews of products.
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
