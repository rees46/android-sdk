package com.personalization.core

/**
 * For the bridges: every push event the SDK sees, of every shop, beside the app's [PushCallbacks] —
 * `Rees46.onPushEvent`. [PushPayload.shopId] is the shop the push went to.
 */
@BridgeApi
fun interface BridgePushListener {
    fun onPushEvent(event: PushEvent, payload: PushPayload)
}
