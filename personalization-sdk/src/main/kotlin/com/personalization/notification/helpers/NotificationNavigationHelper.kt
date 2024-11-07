package com.personalization.notification.helpers

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.personalization.notification.core.RequestCodeGenerator
import com.personalization.notification.domain.NotificationBroadcastReceiver
import com.personalization.notification.model.NotificationConstants.CURRENT_IMAGE_INDEX
import com.personalization.notification.model.NotificationConstants.NOTIFICATION_BODY
import com.personalization.notification.model.NotificationConstants.NOTIFICATION_IMAGES
import com.personalization.notification.model.NotificationConstants.NOTIFICATION_TITLE
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
            putExtra(CURRENT_IMAGE_INDEX, newIndex)
            putExtra(NOTIFICATION_TITLE, data.title)
            putExtra(NOTIFICATION_BODY, data.body)
            putExtra(NOTIFICATION_IMAGES, data.images)
        }
        return PendingIntent.getBroadcast(
            /* context = */ context,
            /* requestCode = */ RequestCodeGenerator.generateRequestCode(action, newIndex),
            /* intent = */ intent,
            /* flags = */ PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
