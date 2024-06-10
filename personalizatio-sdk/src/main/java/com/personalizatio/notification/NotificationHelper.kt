package com.personalizatio.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.core.app.NotificationCompat
import com.rees46.sdk.REES46
import java.io.IOException
import java.io.InputStream
import java.net.URL

object NotificationHelper {

    private const val ACTION_PREVIOUS_IMAGE = "ACTION_PREVIOUS_IMAGE"
    private const val NOTIFICATION_CHANNEL = "notification_channel"
    private const val CURRENT_IMAGE_INDEX = "current_image_index"
    private const val ACTION_NEXT_IMAGE = "ACTION_NEXT_IMAGE"
    private const val NOTIFICATION_TITLE = "title"
    private const val NOTIFICATION_BODY = "body"
    const val NOTIFICATION_IMAGES = "images"

    @JvmStatic
    fun createNotification(
        context: Context,
        data: Map<String?, String?>,
        images: List<Bitmap>?,
        currentIndex: Int
    ) {
        val intent = Intent(context, context::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra(NOTIFICATION_TITLE, data[NOTIFICATION_TITLE])
            putExtra(NOTIFICATION_BODY, data[NOTIFICATION_BODY])
            putExtra(NOTIFICATION_IMAGES, data[NOTIFICATION_IMAGES])
            putExtra(CURRENT_IMAGE_INDEX, currentIndex)
        }

        val pendingIntent = PendingIntent.getActivity(
            /* context = */ context,
            /* requestCode = */ 0,
            /* intent = */ intent,
            /* flags = */ PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL)
            .setContentTitle(data[NOTIFICATION_TITLE])
            .setContentText(data[NOTIFICATION_BODY])
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (!images.isNullOrEmpty()) {
            val currentImage = images[currentIndex]
            notificationBuilder.setLargeIcon(currentImage)
                .setStyle(
                    NotificationCompat.BigPictureStyle().bigPicture(currentImage).bigLargeIcon(null)
                )

            if (currentIndex > 0) {
                val prevPendingIntent = createPendingIntent(
                    context = context,
                    action = ACTION_PREVIOUS_IMAGE,
                    currentIndex = currentIndex,
                    data = data
                )
                notificationBuilder.addAction(
                    NotificationCompat.Action.Builder(
                        android.R.drawable.ic_media_previous,
                        "Назад",
                        prevPendingIntent
                    ).build()
                )
            }

            if (currentIndex < images.size - 1) {
                val nextPendingIntent = createPendingIntent(
                    context = context,
                    action = ACTION_NEXT_IMAGE,
                    currentIndex = currentIndex,
                    data = data
                )
                notificationBuilder.addAction(
                    NotificationCompat.Action.Builder(
                        android.R.drawable.ic_media_next,
                        "Вперед",
                        nextPendingIntent
                    ).build()
                )
            }
        } else {
            notificationBuilder.setStyle(
                NotificationCompat.BigTextStyle().bigText(data[NOTIFICATION_BODY])
            )
        }

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
        if (notificationManager != null) {
            notificationManager.notify(0, notificationBuilder.build())
        } else {
            Log.e(REES46.TAG, "NotificationManager not allowed")
        }
    }

    private fun createPendingIntent(
        context: Context,
        action: String,
        currentIndex: Int,
        data: Map<String?, String?>
    ): PendingIntent {
        val intent = Intent(context, NotificationIntentService::class.java)

        intent.action = action
        intent.putExtra(CURRENT_IMAGE_INDEX, currentIndex)
        intent.putExtra(NOTIFICATION_IMAGES, data[NOTIFICATION_IMAGES])
        intent.putExtra(NOTIFICATION_TITLE, data[NOTIFICATION_TITLE])
        intent.putExtra(NOTIFICATION_BODY, data[NOTIFICATION_BODY])

        return PendingIntent.getService(
            /* context = */ context,
            /* requestCode = */ System.currentTimeMillis().toInt(),
            /* intent = */ intent,
            /* flags = */ PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    @JvmStatic
    fun loadBitmaps(urls: String?): List<Bitmap> {
        val bitmaps = ArrayList<Bitmap>()
        if (urls != null) {
            val urlArray = urls.split(",").toTypedArray()
            for (url in urlArray) {
                try {
                    val inputStream: InputStream = URL(url).openStream()
                    bitmaps.add(BitmapFactory.decodeStream(inputStream))
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
        return bitmaps
    }
}
