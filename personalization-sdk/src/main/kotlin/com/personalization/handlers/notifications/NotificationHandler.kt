package com.personalization.handlers.notifications

import android.content.Context
import android.os.Bundle
import com.personalization.features.notification.data.helpers.NotificationChannelHelper
import com.personalization.features.notification.domain.helpers.NotificationClickProcessor
import com.personalization.sdk.domain.usecases.notification.UpdateNotificationSourceUseCase
import javax.inject.Inject
import org.json.JSONObject

class NotificationHandler @Inject constructor(
  private val updateSourceUseCase: UpdateNotificationSourceUseCase,
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
      onResult = { type, code ->
        updateSourceUseCase(type = type, id = code)
      }
    )
  }
}
