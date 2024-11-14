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
        imageCount: Int,
        hasError: Boolean
    ) {
        setActionButton(
            customView = customView,
            context = context,
            data = data,
            actionId = R.id.action1,
            action = ACTION_PREVIOUS_IMAGE,
            newIndex = currentIndex - 1,
            isVisible = currentIndex > 0
        )

        setActionButton(
            customView = customView,
            context = context,
            data = data,
            actionId = R.id.action2,
            action = ACTION_NEXT_IMAGE,
            newIndex = currentIndex + 1,
            isVisible = currentIndex < imageCount - 1
        )

        setRetryButtonVisibility(
            customView = customView,
            context = context,
            data = data,
            hasError = hasError
        )
    }

    private fun setActionButton(
        customView: RemoteViews,
        context: Context,
        data: NotificationData,
        actionId: Int,
        action: String,
        newIndex: Int,
        isVisible: Boolean
    ) {
        val pendingIntent = NotificationNavigationHelper.createNavigationPendingIntent(
            context = context,
            data = data,
            newIndex = newIndex,
            action = action
        )
        customView.setOnClickPendingIntent(
            /* viewId = */ actionId,
            /* pendingIntent = */ pendingIntent
        )
        customView.setViewVisibility(
            /* viewId = */ actionId,
            /* visibility = */ if (isVisible) View.VISIBLE else View.GONE
        )
    }

    private fun setRetryButtonVisibility(
        customView: RemoteViews,
        context: Context,
        data: NotificationData,
        hasError: Boolean
    ) {
        if (hasError) {
            val retryPendingIntent = NotificationNavigationHelper.createRetryPendingIntent(
                context = context,
                data = data
            )
            customView.setOnClickPendingIntent(
                /* viewId = */ R.id.retryButton,
                /* pendingIntent = */ retryPendingIntent
            )
        }
        customView.setViewVisibility(
            /* viewId = */ R.id.retryButton,
            /* visibility = */ if (hasError) View.VISIBLE else View.GONE
        )
    }
}
