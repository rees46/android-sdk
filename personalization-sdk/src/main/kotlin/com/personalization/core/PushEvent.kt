package com.personalization.core

/**
 * What happened to a push, for `Rees46.handlePush(payload, event)` (decision 0004). Each one is its own
 * request: `track/delivered`, `track/opened`, `track/clicked`, `track/closed`.
 */
enum class PushEvent {
    /** The push reached the app. */
    DELIVERED,

    /** The notification was shown: by the SDK itself, or by the app, which then reports it. */
    OPENED,

    /** The notification was tapped. */
    CLICKED,

    /** The notification was dismissed without a tap. */
    CLOSED,
}
