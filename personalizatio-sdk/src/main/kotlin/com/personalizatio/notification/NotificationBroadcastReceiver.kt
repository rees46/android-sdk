package com.personalizatio.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.personalizatio.SDK
import com.personalizatio.notification.NotificationHelper.ACTION_NEXT_IMAGE
import com.personalizatio.notification.NotificationHelper.ACTION_PREVIOUS_IMAGE
import com.personalizatio.notification.NotificationHelper.CURRENT_IMAGE_INDEX
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action

        val currentIndex = intent.getIntExtra(CURRENT_IMAGE_INDEX, 0)
        val images = intent.getStringExtra(NotificationHelper.NOTIFICATION_IMAGES)
        val title = intent.getStringExtra(NotificationHelper.NOTIFICATION_TITLE)
        val body = intent.getStringExtra(NotificationHelper.NOTIFICATION_BODY)

        CoroutineScope(Dispatchers.Main).launch {
            when (action) {
                ACTION_NEXT_IMAGE, ACTION_PREVIOUS_IMAGE -> updateNotification(
                    context = context,
                    data = mapOf(
                        NotificationHelper.NOTIFICATION_IMAGES to images,
                        NotificationHelper.NOTIFICATION_TITLE to title,
                        NotificationHelper.NOTIFICATION_BODY to body
                    ),
                    newIndex = currentIndex
                )

                else -> Unit
            }
        }
    }

    private suspend fun updateNotification(
        context: Context,
        data: Map<String, String?>,
        newIndex: Int
    ) {
        try {
            NotificationHelper.createNotification(
                context = context,
                data = data,
                images = NotificationHelper.loadBitmaps(
                    urls = data[NotificationHelper.NOTIFICATION_IMAGES]
                ),
                currentIndex = newIndex
            )
        } catch (ioException: IOException) {
            SDK.error("Error caught in load bitmaps", ioException)
        }
    }
}
