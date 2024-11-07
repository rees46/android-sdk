package com.personalization.notification.helpers

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.personalization.notification.core.NotificationHelper
import com.personalization.notification.core.RequestCodeGenerator
import com.personalization.notification.domain.NotificationBroadcastReceiver
import com.personalization.notification.model.PushNotificationData

object NotificationNavigationHelper {

    fun createNavigationPendingIntent(
        context: Context,
        data: PushNotificationData,
        newIndex: Int,
        action: String
    ): PendingIntent {
        val intent = Intent(context, NotificationBroadcastReceiver::class.java).apply {
            this.action = action
            putExtra(NotificationHelper.CURRENT_IMAGE_INDEX, newIndex)
            putExtra(NotificationHelper.NOTIFICATION_TITLE, data.title)
            putExtra(NotificationHelper.NOTIFICATION_BODY, data.body)
            putExtra(NotificationHelper.NOTIFICATION_IMAGES, data.images)
        }
        return PendingIntent.getBroadcast(
            context,
            RequestCodeGenerator.generateRequestCode(action, newIndex),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
