package com.personalization.features.notification.presentation.helpers

import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.personalization.features.notification.domain.model.NotificationConstants.NOTIFICATION_CHANNEL
import com.personalization.resources.NotificationResources
import com.personalization.sdk.data.models.dto.notification.NotificationData
import javax.inject.Inject

class NotificationHelper @Inject constructor(
    private val notificationManager: NotificationManager
) {

    fun createNotification(
        context: Context,
        data: NotificationData,
        images: List<Bitmap>?,
        currentImageIndex: Int = 0,
        hasError: Boolean = false
    ) {
        val view = RemoteViews(
            /* packageName = */ context.packageName,
            /* layoutId = */ NotificationResources.NOTIFICATION_LAYOUT
        )

        NotificationTextHelper.setNotificationText(
            customView = view,
            data = data
        )
        NotificationImageHelper.displayImages(
            customView = view,
            images = images,
            currentIndex = currentImageIndex
        )
        NotificationViewHelper.setNavigationActions(
            customView = view,
            context = context,
            data = data,
            currentIndex = currentImageIndex,
            imageCount = images?.size ?: 0,
            hasError = hasError
        )

        val notificationBuilder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL)
            .setSmallIcon(resolveSmallIcon(context))
            .setCustomContentView(view)
            .setCustomBigContentView(view)
            .setAutoCancel(true)

        notificationManager.notify(
            /* id = */ (data.title + data.body).hashCode(),
            /* notification = */ notificationBuilder.build()
        )
    }

    /**
     * The SDK never imposes its own brand icon. Use the host app's dedicated notification icon
     * if it declared one via the standard `default_notification_icon` meta-data; otherwise fall
     * back to the host application's own launcher icon.
     */
    private fun resolveSmallIcon(context: Context): Int {
        val hostSpecialIcon = readHostNotificationIcon(context)
        return if (hostSpecialIcon != 0) hostSpecialIcon else context.applicationInfo.icon
    }

    private fun readHostNotificationIcon(context: Context): Int = try {
        context.packageManager
            .getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
            .metaData
            ?.getInt(META_DATA_NOTIFICATION_ICON, 0) ?: 0
    } catch (e: PackageManager.NameNotFoundException) {
        0
    }

    private companion object {
        // De-facto standard meta-data hosts use to declare a dedicated push notification icon.
        const val META_DATA_NOTIFICATION_ICON =
            "com.google.firebase.messaging.default_notification_icon"
    }
}
