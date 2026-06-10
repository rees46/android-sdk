package com.personalization

/**
 * Notifies the host app when a push token is received or refreshed for a provider.
 *
 * Registered via [SDK.setOnPushTokenListener]. Fires for every provider available on the
 * device (FCM and/or HMS) once their token is issued — including tokens that the underlying
 * provider only delivers asynchronously (e.g. HMS via `onNewToken`).
 *
 * @param token the push token
 * @param provider the provider that issued the token
 */
fun interface OnPushTokenListener {
    fun onPushToken(token: String, provider: PushProvider)
}
