package com.personalizatio;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

/**
 * Created by Sergey Odintsov
 *
 * @author nixx.dj@gmail.com
 */
final public class MessagingService extends FirebaseMessagingService {

	@Override
	public void onMessageReceived(RemoteMessage remoteMessage) {

		// Check if message contains a data payload.
		if( remoteMessage.getData() != null && remoteMessage.getData().size() > 0 ) {
			SDK.debug("Message data payload: " + remoteMessage.getData());
			SDK.notificationReceived(remoteMessage.getData().get("url"));
			SDK.onMessage(remoteMessage.getData());
		}

		// Check if message contains a notification payload.
		if( remoteMessage.getNotification() != null ) {
			SDK.debug("Message Notification Body: " + remoteMessage.getNotification().getBody());
		}
	}
}
