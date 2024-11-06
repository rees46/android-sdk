package com.personalization.notification.domain

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.personalization.SDK
import com.personalization.notification.core.NotificationHelper.ACTION_NEXT_IMAGE
import com.personalization.notification.core.NotificationHelper.ACTION_PREVIOUS_IMAGE
import com.personalization.notification.core.NotificationHelper.CURRENT_IMAGE_INDEX
import com.personalization.notification.core.NotificationHelper.NOTIFICATION_BODY
import com.personalization.notification.core.NotificationHelper.NOTIFICATION_IMAGES
import com.personalization.notification.core.NotificationHelper.NOTIFICATION_TITLE

class NotificationBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action

        val currentIndex = intent.getIntExtra(CURRENT_IMAGE_INDEX, 0)
        val images = intent.getStringExtra(NOTIFICATION_IMAGES)
        val title = intent.getStringExtra(NOTIFICATION_TITLE)
        val body = intent.getStringExtra(NOTIFICATION_BODY)

        when (action) {
            ACTION_NEXT_IMAGE, ACTION_PREVIOUS_IMAGE -> {
                if (!images.isNullOrEmpty() && !title.isNullOrEmpty() && !body.isNullOrEmpty()) {
                    val inputData = Data.Builder()
                        .putString(NOTIFICATION_IMAGES, images)
                        .putString(NOTIFICATION_TITLE, title)
                        .putString(NOTIFICATION_BODY, body)
                        .putInt(CURRENT_IMAGE_INDEX, currentIndex)
                        .build()

                    val updateNotificationWork = OneTimeWorkRequest.Builder(
                        workerClass = UpdateNotificationWorker::class.java
                    ).setInputData(inputData).build()

                    WorkManager.getInstance(context).enqueue(updateNotificationWork)
                } else {
                    SDK.error("Error caught in onReceive because one of the fields is empty or null")
                }
            }

            else -> SDK.error("Error caught in onReceive due to unknown action $action")
        }
    }
}
