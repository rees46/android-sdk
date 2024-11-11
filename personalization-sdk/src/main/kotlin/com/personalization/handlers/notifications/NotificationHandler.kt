package com.personalization.handlers.notifications

import android.content.Context
import android.os.Bundle
import com.google.firebase.messaging.RemoteMessage
import com.personalization.features.notification.data.helpers.NotificationChannelHelper
import com.personalization.features.notification.data.helpers.NotificationDataMapper
import com.personalization.features.notification.domain.helpers.NotificationClickHandler
import com.personalization.sdk.domain.usecases.notification.UpdateNotificationSourceUseCase
import javax.inject.Inject
import org.json.JSONObject

class NotificationHandler @Inject constructor(
    private val updateSourceUseCase: UpdateNotificationSourceUseCase
) {

    private lateinit var context: Context

    internal fun initialize(context: Context) {
        this.context = context
        createNotificationChannel()
    }

    private fun createNotificationChannel() = NotificationChannelHelper.createNotificationChannel(
        context = context
    )

    fun notificationClicked(
        extras: Bundle?,
        sendAsync: (String, JSONObject) -> Unit
    ) {
        NotificationClickHandler.handleNotificationClick(
            extras = extras,
            sendAsync = sendAsync,
            onResult = { type, code ->
                updateSourceUseCase(
                    type = type,
                    id = code
                )
            }
        )
    }

    fun prepareData(remoteMessage: RemoteMessage): MutableMap<String, String> {
        return NotificationDataMapper.mapRemoteMessageToData(remoteMessage = remoteMessage)
    }
}
