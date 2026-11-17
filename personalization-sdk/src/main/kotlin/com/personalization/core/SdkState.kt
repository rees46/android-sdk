package com.personalization.core

/**
 * Where an instance's initialization (`/init`) stands. Read it from `SDK.state`, follow it with
 * `SDK.onStateChange`.
 *
 * On the legacy core (`Rees46.initialize`) an instance only goes from [INITIALIZING] to [READY]; it is
 * never [RETRYING] or [REFUSED].
 */
enum class SdkState {
    /** Not initialized yet, or released with `release()`: every call fails with `notInitialized`. */
    NOT_INITIALIZED,

    /** `/init` is on its way; requests with a result wait for it. */
    INITIALIZING,

    /** The instance has its `did` and works. */
    READY,

    /**
     * `/init` did not go through (no answer, or an answer to repeat) and is being repeated. With a
     * `did` from an earlier launch the instance works as [READY]; without one, as [INITIALIZING].
     */
    RETRYING,

    /** The shop turned the SDK away (`402`, `403`, `404` or another `4xx` on `/init`): calls fail with `shopBlocked`. */
    REFUSED,
}
