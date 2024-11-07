package com.personalization.features.notification.presentation.helpers

import android.widget.RemoteViews
import com.personalization.R
import com.personalization.features.notification.domain.model.NotificationData

object NotificationTextHelper {

    fun setNotificationText(
        customView: RemoteViews,
        data: NotificationData
    ) {
        customView.setTextViewText(
            /* viewId = */ R.id.title,
            /* text = */ data.title
        )
        customView.setTextViewText(
            /* viewId = */ R.id.body,
            /* text = */ data.body
        )
    }
}