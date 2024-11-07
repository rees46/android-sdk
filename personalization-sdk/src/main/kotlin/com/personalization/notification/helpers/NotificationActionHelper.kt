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

object NotificationActionHelper {

    fun setNavigationActions(
        customView: RemoteViews,
        context: Context,
        data: Map<String, String?>,
        currentIndex: Int,
        imageCount: Int
    ) {
        val prevPendingIntent = createNavigationPendingIntent(
            context, data, currentIndex - 1, NotificationHelper.ACTION_PREVIOUS_IMAGE
        )
        val nextPendingIntent = createNavigationPendingIntent(
            context, data, currentIndex + 1, NotificationHelper.ACTION_NEXT_IMAGE
        )

        customView.setOnClickPendingIntent(R.id.action1, prevPendingIntent)
        customView.setOnClickPendingIntent(R.id.action2, nextPendingIntent)

        customView.setViewVisibility(
            R.id.action1,
            if (currentIndex > 0) View.VISIBLE else View.GONE
        )
        customView.setViewVisibility(
            R.id.action2,
            if (currentIndex < imageCount - 1) View.VISIBLE else View.GONE
        )
    }

    private fun createNavigationPendingIntent(
        context: Context,
        data: Map<String, String?>,
        newIndex: Int,
        action: String
    ): PendingIntent {
        val intent = Intent(context, NotificationBroadcastReceiver::class.java).apply {
            this.action = action
            putExtra(NotificationHelper.CURRENT_IMAGE_INDEX, newIndex)
            putExtra(NotificationHelper.NOTIFICATION_TITLE, data[NotificationHelper.NOTIFICATION_TITLE])
            putExtra(NotificationHelper.NOTIFICATION_BODY, data[NotificationHelper.NOTIFICATION_BODY])
            putExtra(NotificationHelper.NOTIFICATION_IMAGES, data[NotificationHelper.NOTIFICATION_IMAGES])
        }
        return PendingIntent.getBroadcast(
            context,
            RequestCodeGenerator.generateRequestCode(action, newIndex),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
