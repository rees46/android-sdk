package com.personalization.features.notification.data.mapper

import android.content.Intent
import com.personalization.features.notification.domain.model.NotificationConstants.NOTIFICATION_ACTIONS
import com.personalization.features.notification.domain.model.NotificationConstants.NOTIFICATION_ACTION_URLS
import com.personalization.features.notification.domain.model.NotificationConstants.NOTIFICATION_BODY
import com.personalization.features.notification.domain.model.NotificationConstants.NOTIFICATION_EVENT
import com.personalization.features.notification.domain.model.NotificationConstants.NOTIFICATION_ICON
import com.personalization.features.notification.domain.model.NotificationConstants.NOTIFICATION_IMAGE
import com.personalization.features.notification.domain.model.NotificationConstants.NOTIFICATION_PARAM_ID
import com.personalization.features.notification.domain.model.NotificationConstants.NOTIFICATION_TITLE
import com.personalization.features.notification.domain.model.NotificationConstants.TYPE_PARAM
import com.personalization.features.notification.domain.model.NotificationData

fun Intent.toNotificationData(): NotificationData = NotificationData(
    id = getStringExtra(NOTIFICATION_PARAM_ID),
    title = getStringExtra(NOTIFICATION_TITLE),
    body = getStringExtra(NOTIFICATION_BODY),
    icon = getStringExtra(NOTIFICATION_ICON),
    type = getStringExtra(TYPE_PARAM),
    actions = parseNotificationActions(getStringExtra(NOTIFICATION_ACTIONS)),
    actionUrls = parseActionUrls(getStringExtra(NOTIFICATION_ACTION_URLS)),
    image = getStringExtra(NOTIFICATION_IMAGE),
    event = parseNotificationEvent(getStringExtra(NOTIFICATION_EVENT))
)
