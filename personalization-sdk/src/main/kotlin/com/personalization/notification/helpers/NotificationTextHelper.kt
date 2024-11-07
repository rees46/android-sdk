package com.personalization.notification.helpers

import android.widget.RemoteViews
import com.personalization.R
import com.personalization.notification.core.NotificationHelper

object NotificationTextHelper {

    fun setNotificationText(customView: RemoteViews, data: Map<String, String?>) {
        customView.setTextViewText(R.id.title, data[NotificationHelper.NOTIFICATION_TITLE])
        customView.setTextViewText(R.id.body, data[NotificationHelper.NOTIFICATION_BODY])
    }
}
