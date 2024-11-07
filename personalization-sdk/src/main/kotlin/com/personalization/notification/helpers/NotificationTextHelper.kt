package com.personalization.notification.helpers

import android.widget.RemoteViews
import com.personalization.R
import com.personalization.notification.model.NotificationData

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
