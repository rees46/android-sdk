package com.personalization

/**
 * The push lifecycle event a host reports through [Rees46.handlePush].
 *
 * [RECEIVED] tracks delivery (`track/received`) and forwards the payload to the host's message
 * listener; [CLICKED] tracks the tap (`track/clicked`) and runs the click action.
 */
enum class PushEventType {
    RECEIVED,
    CLICKED
}
