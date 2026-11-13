package com.personalization.handlers.notifications

import android.content.Context
import android.os.Bundle
import com.personalization.features.notification.data.helpers.NotificationChannelHelper
import com.personalization.features.notification.domain.helpers.NotificationClickProcessor
import com.personalization.sdk.domain.usecases.trackingSource.SetTrackingSourceUseCase
import javax.inject.Inject
import org.json.JSONObject

class NotificationHandler @Inject constructor(
    private val setTrackingSourceUseCase: SetTrackingSourceUseCase,
    private val notificationClickProcessor: NotificationClickProcessor
) {

    private lateinit var context: Context

    internal fun initialize(context: Context) {
        this.context = context
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        NotificationChannelHelper.createNotificationChannel(context = context)
    }

    fun notificationClicked(
        extras: Bundle?,
        sendAsync: (String, JSONObject) -> Unit
    ) {
        notificationClickProcessor.processClick(
            extras = extras,
            sendAsync = sendAsync,
            // The click attributes the next 48 hours of `/push` events as their `source`, the same
            // store `tracking.setSource` writes. It does not go as top-level `from` and `code`:
            // `/init` takes those for a new click of the mailing, on every launch.
            onResult = { type, code ->
                setTrackingSourceUseCase(type = type, code = code)
            }
        )
    }
}
