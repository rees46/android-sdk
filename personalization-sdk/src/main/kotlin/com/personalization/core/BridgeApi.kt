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
 * - `TrackingApi.queued` — the events of `sdk.tracking` as its methods send them, each completed once the
 *   queue accepted it, as the contract completes them; the methods of `sdk.tracking` hear the server's answer
 *   until the major release;
 * - the namespaces of the new core, where each method is a suspending function with a twin that ends in a
 *   [CompletionListener], and fails with [SdkError] — [SdkError.NotInitialized] on the legacy core, where
 *   they do not run, and [SdkError.InvalidArgument], before any request, for an argument the contract
 *   refuses:
 *   - `SDK.cart` — the customer's cart;
 *   - `SDK.products`, `SDK.collections`, `SDK.recommendations` and `SDK.search` — the catalog, its
 *     collections and recommendation blocks, and the search, the search agent on `Rees46Config.appDomain`
 *     among it;
 *   - `SDK.profile`, `SDK.subscriptions` and `SDK.segments` — the customer's profile, subscriptions and
 *     segments, and the device's A/B group;
 *   - `SDK.notifications` — the push events a bridge that parses its pushes reports itself, through the
 *     queue once per message, the push token and the push callbacks of one shop;
 *   - `SDK.stories`, `SDK.predict` and `SDK.loyalty` — the stories blocks, the probability to purchase, and
 *     the loyalty program on the customer's side;
 *   - `SDK.sliders` — banner sliders, whose banners' events are `TrackingApi.bannerView` and `bannerClick`,
 *     through the new core's queue only, in the public `tracking` namespace;
 *   - `SDK.reviews` — NPS, CSAT and CES ratings and their dictionaries on `Rees46Config.appDomain`, and the
 *     reviews of products.
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
