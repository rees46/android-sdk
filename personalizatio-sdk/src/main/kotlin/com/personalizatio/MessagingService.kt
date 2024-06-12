package com.personalizatio

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        //TODO Implement if needed
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // Check if message contains a data payload.

        if (remoteMessage.data.isNotEmpty()) {
            SDK.debug("Message data payload: ${remoteMessage.data}")
            SDK.onMessage(remoteMessage)
        }

        // Check if message contains a notification payload.
        if (remoteMessage.notification != null) {
            SDK.debug("Message Notification Body: ${remoteMessage.notification?.body.orEmpty()}")
        }
    }
}
