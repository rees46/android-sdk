package com.personalization.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.personalization.R
import com.personalization.SDK
import java.io.IOException
import java.io.InputStream
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object NotificationHelper {

    const val TAG = "NotificationHelper"

    private const val NOTIFICATION_CHANNEL = "notification_channel"
    const val ACTION_PREVIOUS_IMAGE = "ACTION_PREVIOUS_IMAGE"
    const val CURRENT_IMAGE_INDEX = "current_image_index"
    const val ACTION_NEXT_IMAGE = "ACTION_NEXT_IMAGE"
    const val NOTIFICATION_IMAGES = "images"
    const val NOTIFICATION_TITLE = "title"
    const val NOTIFICATION_BODY = "body"

    var notificationType: String = "NOTIFICATION_TYPE"
    var notificationId: String = "NOTIFICATION_ID"

    private val requestCodeGenerator = RequestCodeGenerator

    fun createNotification(
        context: Context,
        data: Map<String, String?>,
        images: List<Bitmap>?,
        currentIndex: Int
    ) {
        val customView = RemoteViews(context.packageName, R.layout.custom_notification)

        setNotificationText(customView, data)
        configureImageDisplay(customView, images, currentIndex)
        setNavigationActions(customView, context, data, currentIndex, images?.size ?: 0)

        val notificationBuilder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setCustomContentView(customView)
            .setCustomBigContentView(customView)
            .setAutoCancel(true)

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId.hashCode(), notificationBuilder.build())
    }

    private fun configureImageDisplay(
        customView: RemoteViews,
        images: List<Bitmap>?,
        currentIndex: Int
    ) {
        if (!images.isNullOrEmpty() && currentIndex in images.indices) {
            customView.setViewVisibility(R.id.smallImage, View.VISIBLE)
            customView.setViewVisibility(R.id.largeImage, View.VISIBLE)
            customView.setImageViewBitmap(R.id.smallImage, images[currentIndex])
            customView.setImageViewBitmap(R.id.largeImage, images[currentIndex])
            customView.setImageViewResource(R.id.expandArrow, R.drawable.ic_arrow_open)
            customView.setViewVisibility(R.id.actionContainer, View.VISIBLE)
        } else {
            customView.setViewVisibility(R.id.smallImage, View.GONE)
            customView.setViewVisibility(R.id.expandArrow, View.GONE)
            customView.setViewVisibility(R.id.actionContainer, View.GONE)
        }
    }

    private fun setNotificationText(customView: RemoteViews, data: Map<String, String?>) {
        customView.setTextViewText(R.id.title, data[NOTIFICATION_TITLE])
        customView.setTextViewText(R.id.body, data[NOTIFICATION_BODY])
    }

    private fun setNavigationActions(
        customView: RemoteViews,
        context: Context,
        data: Map<String, String?>,
        currentIndex: Int,
        imageCount: Int
    ) {
        val prevPendingIntent = createNavigationPendingIntent(
            context, data, currentIndex - 1, ACTION_PREVIOUS_IMAGE
        )
        val nextPendingIntent = createNavigationPendingIntent(
            context, data, currentIndex + 1, ACTION_NEXT_IMAGE
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
            putExtra(CURRENT_IMAGE_INDEX, newIndex)
            putExtra(NOTIFICATION_TITLE, data[NOTIFICATION_TITLE])
            putExtra(NOTIFICATION_BODY, data[NOTIFICATION_BODY])
            putExtra(NOTIFICATION_IMAGES, data[NOTIFICATION_IMAGES])
        }
        return PendingIntent.getBroadcast(
            context,
            requestCodeGenerator.generateRequestCode(action, newIndex),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    suspend fun loadBitmaps(urls: String?): List<Bitmap> {
        val bitmaps = mutableListOf<Bitmap>()
        if (urls != null) {
            val urlArray = urls.split(",").toTypedArray()
            withContext(Dispatchers.IO) {
                for (url in urlArray) {
                    try {
                        val inputStream: InputStream = URL(url).openStream()
                        bitmaps.add(BitmapFactory.decodeStream(inputStream))
                    } catch (ioException: IOException) {
                        SDK.error("Error caught in load bitmaps", ioException)
                    }
                }
            }
        }
        return bitmaps
    }
}
