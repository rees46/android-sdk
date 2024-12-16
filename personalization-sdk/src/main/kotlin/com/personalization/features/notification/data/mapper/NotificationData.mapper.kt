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

fun RemoteMessage.toNotificationData(): NotificationData = NotificationData(
    id = this.data[NOTIFICATION_PARAM_ID],
    title = this.data[NOTIFICATION_TITLE],
    body = this.data[NOTIFICATION_BODY],
    icon = this.data[NOTIFICATION_ICON],
    type = this.data[TYPE_PARAM],
    actions = parseNotificationActions(this.data[NOTIFICATION_ACTIONS]),
    actionUrls = parseActionUrls(this.data[NOTIFICATION_ACTION_URLS]),
    image = this.data[NOTIFICATION_IMAGE],
    event = parseNotificationEvent(this.data[NOTIFICATION_EVENT])
)
