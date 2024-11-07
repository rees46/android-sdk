package com.personalization.features.notification.presentation.helpers

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.personalization.features.notification.core.RequestCodeGenerator
import com.personalization.features.notification.data.broadcast.NotificationBroadcastReceiver
import com.personalization.features.notification.domain.model.NotificationConstants.CURRENT_IMAGE_INDEX
import com.personalization.features.notification.domain.model.NotificationConstants.NOTIFICATION_BODY
import com.personalization.features.notification.domain.model.NotificationConstants.NOTIFICATION_IMAGES
import com.personalization.features.notification.domain.model.NotificationConstants.NOTIFICATION_TITLE
import com.personalization.features.notification.domain.model.NotificationData

object NotificationNavigationHelper {

    fun createNavigationPendingIntent(
        context: Context,
        data: NotificationData,
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