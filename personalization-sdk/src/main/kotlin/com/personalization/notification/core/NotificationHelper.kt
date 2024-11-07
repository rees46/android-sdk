package com.personalization.notification.core

import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.personalization.R
import com.personalization.notification.helpers.NotificationActionHelper
import com.personalization.notification.helpers.NotificationImageHelper
import com.personalization.notification.helpers.NotificationTextHelper

object NotificationHelper {

    private const val NOTIFICATION_CHANNEL = "notification_channel"
    const val ACTION_PREVIOUS_IMAGE = "ACTION_PREVIOUS_IMAGE"
    const val CURRENT_IMAGE_INDEX = "current_image_index"
    const val ACTION_NEXT_IMAGE = "ACTION_NEXT_IMAGE"
    const val NOTIFICATION_IMAGES = "images"
    const val NOTIFICATION_TITLE = "title"
    const val NOTIFICATION_BODY = "body"

    fun createNotification(
        context: Context,
        notificationId: Int,
        data: Map<String, String?>,
        images: List<Bitmap>?,
        currentIndex: Int
    ) {
        val customView = RemoteViews(context.packageName, R.layout.custom_notification)

        NotificationTextHelper.setNotificationText(
            customView = customView,
            data = data
        )
        NotificationImageHelper.displayImages(
            customView = customView,
            images = images,
            currentIndex = currentIndex
        )
        NotificationActionHelper.setNavigationActions(
            customView = customView,
            context = context,
            data = data,
            currentIndex = currentIndex,
            imageCount = images?.size ?: 0
        )

        val notificationBuilder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setCustomContentView(customView)
            .setCustomBigContentView(customView)
            .setAutoCancel(true)

        val notificationManager = context.getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager
        notificationManager.notify(
            notificationId,
            notificationBuilder.build()
        )
    }
}
