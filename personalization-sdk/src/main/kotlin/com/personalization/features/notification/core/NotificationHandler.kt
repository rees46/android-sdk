package com.personalization.features.notification.core

import android.content.Context
import android.os.Bundle
import com.google.firebase.messaging.RemoteMessage
import com.personalization.features.notification.data.helpers.NotificationChannelHelper
import com.personalization.features.notification.data.helpers.NotificationDataMapper
import com.personalization.features.notification.domain.helpers.NotificationClickHandler
import javax.inject.Inject
import org.json.JSONObject

class NotificationHandler @Inject constructor(
    private val notificationClickHandler: NotificationClickHandler
) {

    private lateinit var context: Context

    internal fun initialize(context: Context) {
        this.context = context
        NotificationChannelHelper.createNotificationChannel(context = context)
    }

    fun notificationClicked(
        extras: Bundle?,
        sendAsync: (String, JSONObject) -> Unit
    ) {
        notificationClickHandler.handleNotificationClick(
            extras = extras,
            sendAsync = sendAsync
        )
    }

    fun prepareData(remoteMessage: RemoteMessage): MutableMap<String, String> {
        return NotificationDataMapper.mapRemoteMessageToData(remoteMessage = remoteMessage)
    }

    companion object {
        private const val NOTIFICATION_TYPE = "NOTIFICATION_TYPE"
        private const val NOTIFICATION_ID = "NOTIFICATION_ID"
        private const val TRACK_CLICKED = "track/clicked"
        private const val TAG = "NotificationHandler"
        private const val IMAGES_FIELD = "images"
        private const val TITLE_FIELD = "title"
        private const val IMAGE_FIELD = "image"
        private const val ANALYTICS_LABEL_FIELD = "analytics_label"
        private const val BODY_FIELD = "body"
        private const val TYPE_PARAM = "type"
        private const val CODE_PARAM = "code"
    }
}
