package com.personalization.notification.helpers

import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.personalization.R
import com.personalization.notification.core.NotificationHelper
import com.personalization.notification.model.PushNotificationData

object NotificationViewHelper {

    fun setNavigationActions(
        customView: RemoteViews,
        context: Context,
        data: PushNotificationData,
        currentIndex: Int,
        imageCount: Int
    ) {
        val prevPendingIntent = NotificationNavigationHelper.createNavigationPendingIntent(
            context = context,
            data = data,
            newIndex = currentIndex - 1,
            action = NotificationHelper.ACTION_PREVIOUS_IMAGE
        )
        val nextPendingIntent = NotificationNavigationHelper.createNavigationPendingIntent(
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
            /* visibility = */ if (currentIndex > 0) View.VISIBLE else View.GONE
        )
        customView.setViewVisibility(
            /* viewId = */ R.id.action2,
            /* visibility = */ if (currentIndex < imageCount - 1) View.VISIBLE else View.GONE
        )
    }
}
