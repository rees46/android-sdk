package com.personalizatio.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.personalizatio.notification.NotificationHelper.ACTION_NEXT_IMAGE
import com.personalizatio.notification.NotificationHelper.ACTION_PREVIOUS_IMAGE
import com.personalizatio.notification.NotificationHelper.CURRENT_IMAGE_INDEX
import java.io.IOException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class NotificationBroadcastReceiver : BroadcastReceiver() {

    @OptIn(DelicateCoroutinesApi::class)
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val currentIndex = intent.getIntExtra(CURRENT_IMAGE_INDEX, 0)
        val images = intent.getStringExtra(NotificationHelper.NOTIFICATION_IMAGES)
        val title = intent.getStringExtra(NotificationHelper.NOTIFICATION_TITLE)
        val body = intent.getStringExtra(NotificationHelper.NOTIFICATION_BODY)

        val data = mapOf(
            NotificationHelper.NOTIFICATION_IMAGES to images,
            NotificationHelper.NOTIFICATION_TITLE to title,
            NotificationHelper.NOTIFICATION_BODY to body
        )

        GlobalScope.launch {
            when (action) {
                ACTION_NEXT_IMAGE -> {
                    val newIndex = currentIndex
                    updateNotification(context, data, newIndex)
                }

                ACTION_PREVIOUS_IMAGE -> {
                    val newIndex = currentIndex
                    updateNotification(context, data, newIndex)
                }

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
            val urls = data[NotificationHelper.NOTIFICATION_IMAGES]
            val bitmaps = NotificationHelper.loadBitmaps(urls)
            NotificationHelper.createNotification(
                context = context,
                data = data,
                images = bitmaps,
                currentIndex = newIndex
            )
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}