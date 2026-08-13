package com.personalization

import com.personalization.sdk.data.models.dto.notification.NotificationData

/**
 * Process-global push-message callback, set once through [Rees46.setOnMessageListener].
 *
 * Invoked for every received push, on whatever shop it routes to, with that shop's [shopId]. One
 * registration covers every shop — unlike the per-instance [OnMessageListener], which has to be set on
 * each [SDK] instance and silently shows nothing for a shop it was never set on. Displaying the
 * notification is the host's job (the SDK tracks `received` on its own); build and post it from [data].
 */
fun interface OnShopMessageListener {
    fun onMessage(shopId: String, data: NotificationData)
}
