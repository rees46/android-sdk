package com.personalization.push

import android.content.Context
import com.personalization.PushProvider

/**
 * A provider-specific source of push tokens.
 *
 * Implementations encapsulate everything that differs between providers: how to detect
 * availability on the device and how to proactively fetch the current token. Adding a new
 * provider means adding a [PushTokenSource] (and a thin messaging service that forwards
 * `onNewToken` to [com.personalization.SDK.onPushTokenReceived]).
 */
interface PushTokenSource {

    val provider: PushProvider

    /** Whether this provider's mobile services are available on the current device. */
    fun isAvailable(context: Context): Boolean

    /**
     * Proactively fetches the current token if the provider supports it. Some providers
     * (e.g. HMS) deliver the first token only asynchronously via their messaging service,
     * in which case this may complete with an empty token — the real one arrives later.
     */
    fun fetchToken(context: Context, callback: (Result<String?>) -> Unit)
}
