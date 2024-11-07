package com.personalization.features.notification.presentation.helpers

import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.personalization.R
import com.personalization.features.notification.domain.model.NotificationConstants.ACTION_NEXT_IMAGE
import com.personalization.features.notification.domain.model.NotificationConstants.ACTION_PREVIOUS_IMAGE
import com.personalization.features.notification.domain.model.NotificationData

object NotificationViewHelper {

    fun setNavigationActions(
        customView: RemoteViews,
        context: Context,
        data: NotificationData,
        currentIndex: Int,
        imageCount: Int
    ) {
        val prevPendingIntent = NotificationNavigationHelper.createNavigationPendingIntent(
            context = context,
            data = data,
            newIndex = currentIndex - 1,
            action = ACTION_PREVIOUS_IMAGE
        )
        val nextPendingIntent = NotificationNavigationHelper.createNavigationPendingIntent(
            context = context,
            data = data,
            newIndex = currentIndex + 1,
            action = ACTION_NEXT_IMAGE
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
