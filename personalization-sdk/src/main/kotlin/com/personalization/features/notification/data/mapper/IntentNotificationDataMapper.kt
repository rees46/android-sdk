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

fun Intent.toNotificationData(): NotificationData {
    val id = getStringExtra(NOTIFICATION_PARAM_ID)
    val title = getStringExtra(NOTIFICATION_TITLE)
    val body = getStringExtra(NOTIFICATION_BODY)
    val icon = getStringExtra(NOTIFICATION_ICON)
    val type = getStringExtra(TYPE_PARAM)
    val actionsJson = getStringExtra(NOTIFICATION_ACTIONS)
    val actionUrlsJson = getStringExtra(NOTIFICATION_ACTION_URLS)
    val image = getStringExtra(NOTIFICATION_IMAGE)
    val eventJson = getStringExtra(NOTIFICATION_EVENT)

    return NotificationData(
        id = id,
        title = title,
        body = body,
        icon = icon,
        type = type,
        actions = parseNotificationActions(actionsJson),
        actionUrls = parseActionUrls(actionUrlsJson),
        image = image,
        event = parseNotificationEvent(eventJson)
    )
}
