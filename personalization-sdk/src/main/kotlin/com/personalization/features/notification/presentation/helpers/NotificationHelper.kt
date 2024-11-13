package com.personalization.features.notification.presentation.helpers

import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.personalization.features.notification.domain.model.NotificationConstants.NOTIFICATION_CHANNEL
import com.personalization.features.notification.domain.model.NotificationData
import com.personalization.resources.NotificationResources
import javax.inject.Inject

class NotificationHelper @Inject constructor(
  private val notificationManager: NotificationManager
) {

  private var notificationId: String? = null

  fun setNotificationId(id: String) {
    this.notificationId = id
  }

  fun createNotification(
    context: Context,
    notificationId: Int,
    data: NotificationData,
    images: List<Bitmap>?,
    currentImageIndex: Int = 0
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
      imageCount = images?.size ?: 0
    )

    val notificationBuilder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL)
      .setSmallIcon(NotificationResources.NOTIFICATION_ICON)
      .setCustomContentView(view)
      .setCustomBigContentView(view)
      .setAutoCancel(true)

    notificationManager.notify(
      (this.notificationId ?: notificationId).hashCode(),
      notificationBuilder.build()
    )
  }
}
