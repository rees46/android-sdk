package com.personalization.features.notification.data.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.personalization.features.notification.core.ErrorHandler
import com.personalization.features.notification.data.worker.UpdateNotificationWorker
import com.personalization.features.notification.domain.model.NotificationConstants.ACTION_NEXT_IMAGE
import com.personalization.features.notification.domain.model.NotificationConstants.ACTION_PREVIOUS_IMAGE
import com.personalization.features.notification.domain.model.NotificationConstants.CURRENT_IMAGE_INDEX
import com.personalization.features.notification.domain.model.NotificationConstants.NOTIFICATION_BODY
import com.personalization.features.notification.domain.model.NotificationConstants.NOTIFICATION_IMAGES
import com.personalization.features.notification.domain.model.NotificationConstants.NOTIFICATION_TITLE

class NotificationBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val currentIndex = intent.getIntExtra(CURRENT_IMAGE_INDEX, 0)
        val images = intent.getStringExtra(NOTIFICATION_IMAGES)
        val title = intent.getStringExtra(NOTIFICATION_TITLE)
        val body = intent.getStringExtra(NOTIFICATION_BODY)

        if (action == ACTION_NEXT_IMAGE || action == ACTION_PREVIOUS_IMAGE) {
            if (!images.isNullOrEmpty() && !title.isNullOrEmpty() && !body.isNullOrEmpty()) {
                val inputData = Data.Builder()
                    .putString(NOTIFICATION_IMAGES, images)
                    .putString(NOTIFICATION_TITLE, title)
                    .putString(NOTIFICATION_BODY, body)
                    .putInt(CURRENT_IMAGE_INDEX, currentIndex)
                    .build()

                val updateNotificationWork = OneTimeWorkRequestBuilder<UpdateNotificationWorker>()
                    .setInputData(inputData)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build()) // Ограничение на сеть, если необходимо
                    .build()

                WorkManager.getInstance(context).enqueueUniqueWork(
                    "update_notification_work",
                    ExistingWorkPolicy.APPEND_OR_REPLACE,
                    updateNotificationWork
                )
            } else {
                ErrorHandler.logError("Error caught in onReceive because one of the fields is empty or null")
            }
        } else {
            ErrorHandler.logError("Error caught in onReceive due to unknown action $action")
        }
    }
}