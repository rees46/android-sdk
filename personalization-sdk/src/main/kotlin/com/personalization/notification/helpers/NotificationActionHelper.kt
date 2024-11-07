package com.personalization.notification.helpers

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.personalization.R
import com.personalization.notification.core.NotificationHelper
import com.personalization.notification.core.RequestCodeGenerator
import com.personalization.notification.domain.NotificationBroadcastReceiver
import com.personalization.notification.model.PushNotificationData

object NotificationActionHelper {

    fun setNavigationActions(
        customView: RemoteViews,
        context: Context,
        data: PushNotificationData,
        currentIndex: Int,
        imageCount: Int
    ) {
        val prevPendingIntent = createNavigationPendingIntent(
            context = context,
            data = data,
            newIndex = currentIndex - 1,
            action = NotificationHelper.ACTION_PREVIOUS_IMAGE
        )
        val nextPendingIntent = createNavigationPendingIntent(
            context = context,
            data = data,
            newIndex = currentIndex + 1,
            action = NotificationHelper.ACTION_NEXT_IMAGE
        )

        customView.setOnClickPendingIntent(
            /* viewId = */ R.id.action1,
            /* pendingIntent = */ prevPendingIntent
        )
        customView.setOnClickPendingIntent(
            /* viewId = */ R.id.action2,
            /* pendingIntent = */ nextPendingIntent
        )

        customView.setViewVisibility(
            /* viewId = */ R.id.action1,
            /* visibility = */ when {
                currentIndex > 0 -> View.VISIBLE
                else -> View.GONE
            }
        )
        customView.setViewVisibility(
            /* viewId = */ R.id.action2,
            /* visibility = */ when {
                currentIndex < imageCount - 1 -> View.VISIBLE
                else -> View.GONE
            }
        )
    }

    private fun createNavigationPendingIntent(
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
