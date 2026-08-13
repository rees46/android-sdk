package com.personalization

import com.huawei.hms.push.HmsMessageService
import com.huawei.hms.push.RemoteMessage

class HmsMessagingService : HmsMessageService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        if (token.isNotEmpty()) {
            SDK.onPushTokenReceived(token, PushProvider.HMS)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // Never let a failure handling a push propagate to the HMS thread and crash the host app.
        try {
            val data = message.dataOfMap
            // Data-only messages (attach_notification=false) are not shown by HMS Core, so route
            // them through the facade to be tracked and displayed via the OnMessageListener — the same
            // path FCM data-messages take. Going through Rees46.handlePush also materializes a
            // lazily-registered shop the push targets, so a cold-process push (app not running) reaches
            // the second shop instead of being dropped for not being live yet.
            if (data.isNotEmpty()) {
                SDK.debug("HMS message data: $data")
                Rees46.handlePush(data, PushEventType.RECEIVED)
            }
        } catch (throwable: Throwable) {
            SDK.error("Failed to handle HMS push message", throwable)
        }
    }
}
