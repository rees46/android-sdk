package com.personalization

import com.personalization.logs.SdkLogs
import com.personalization.logs.LogCodes
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        if (token.isNotEmpty()) {
            SDK.onPushTokenReceived(token, PushProvider.FCM)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // Never let a failure handling a push propagate to the FCM thread and crash the host app.
        try {
            // Check if message contains a data payload.
            if (remoteMessage.data.isNotEmpty()) {
                SdkLogs.log(LogLevel.DEBUG, LogCodes.PUSH_RECEIVED, "FCM data message", remoteMessage.data["shop_id"], mapOf("keys" to remoteMessage.data.keys.sorted()))
                // Route through the facade so a lazily-registered shop the push targets is materialized
                // on demand — the cold process FCM starts to deliver a data-only message only has the
                // eagerly-initialized shops live, so a lazy shop would otherwise be dropped at routing.
                Rees46.handlePush(remoteMessage.data, PushEventType.RECEIVED)
            }

            // SDK pushes are data messages; one with only a notification payload is not the SDK's.
            if (remoteMessage.data.isEmpty() && remoteMessage.notification != null) {
                SdkLogs.log(LogLevel.WARNING, LogCodes.PUSH_DROPPED, "FCM message with only a notification payload is not an SDK push")
            }
        } catch (throwable: Throwable) {
            SdkLogs.log(LogLevel.ERROR, LogCodes.PUSH_FAILED, "An FCM message could not be handled", throwable = throwable)
        }
    }
}
