package com.personalization.features.notification.data.mapper

import com.google.firebase.messaging.RemoteMessage
import com.personalization.features.notification.actions.mapper.parseActionUrls
import com.personalization.features.notification.actions.mapper.parseNotificationActions
import com.personalization.features.notification.domain.model.NotificationConstants.NOTIFICATION_ACTIONS
import com.personalization.features.notification.domain.model.NotificationConstants.NOTIFICATION_ACTION_URLS
import com.personalization.features.notification.domain.model.NotificationConstants.NOTIFICATION_BODY
import com.personalization.features.notification.domain.model.NotificationConstants.NOTIFICATION_EVENT
import com.personalization.features.notification.domain.model.NotificationConstants.NOTIFICATION_ICON
import com.personalization.features.notification.domain.model.NotificationConstants.NOTIFICATION_IMAGE
import com.personalization.features.notification.domain.model.NotificationConstants.NOTIFICATION_PARAM_ID
import com.personalization.features.notification.domain.model.NotificationConstants.NOTIFICATION_TITLE
import com.personalization.features.notification.domain.model.NotificationConstants.TYPE_PARAM
import com.personalization.features.notification.event.mapper.parseNotificationEvent
import com.personalization.sdk.data.models.dto.notification.NotificationData

/**
 * Maps a push `data` payload to [NotificationData]. Provider-agnostic — both the FCM and the HMS
 * messaging services deliver the payload as a `data` map with the same keys, so both route through
 * here (see [RemoteMessage.toNotificationData] for FCM and HmsMessagingService for HMS).
 */
fun Map<String, String>.toNotificationData(): NotificationData = NotificationData(
    id = this[NOTIFICATION_PARAM_ID],
    title = this[NOTIFICATION_TITLE],
    body = this[NOTIFICATION_BODY],
    icon = this[NOTIFICATION_ICON],
    type = this[TYPE_PARAM],
    actions = parseNotificationActions(this[NOTIFICATION_ACTIONS]),
    actionUrls = parseActionUrls(this[NOTIFICATION_ACTION_URLS]),
    image = this[NOTIFICATION_IMAGE],
    event = parseNotificationEvent(this[NOTIFICATION_EVENT])
)

/** FCM convenience: unwraps the message's data map. */
fun RemoteMessage.toNotificationData(): NotificationData = this.data.toNotificationData()
