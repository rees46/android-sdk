package com.personalizatio

import com.google.firebase.messaging.FirebaseMessagingService

/**
 * Created by Sergey Odintsov
 *
 * @author nixx.dj@gmail.com
 */
class MessagingService : FirebaseMessagingService() {
    @Override
    fun onMessageReceived(remoteMessage: RemoteMessage?) {
        // Check if message contains a data payload.

        if (remoteMessage.getData().size() > 0) {
            SDK.debug("Message data payload: " + remoteMessage.getData())
            SDK.onMessage(remoteMessage)
        }

        // Check if message contains a notification payload.
        if (remoteMessage.getNotification() != null) {
            SDK.debug("Message Notification Body: " + remoteMessage.getNotification().getBody())
        }
    }
}
