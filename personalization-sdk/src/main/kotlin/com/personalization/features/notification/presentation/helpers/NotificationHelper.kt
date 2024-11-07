package com.personalization.features.notification.presentation.helpers

import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.personalization.R
import com.personalization.features.notification.domain.model.NotificationConstants.NOTIFICATION_CHANNEL
import com.personalization.features.notification.domain.model.NotificationData

object NotificationHelper {

    var notificationId: String? = null

    fun createNotification(
        context: Context,
        notificationId: Int,
        data: NotificationData,
        images: List<Bitmap>?,
        currentIndex: Int = 0
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
        NotificationViewHelper.setNavigationActions(
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

        val notificationManager: NotificationManager = context.getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager

        notificationManager.notify(
            (NotificationHelper.notificationId ?: notificationId).hashCode(),
            notificationBuilder.build()
        )
    }
}
