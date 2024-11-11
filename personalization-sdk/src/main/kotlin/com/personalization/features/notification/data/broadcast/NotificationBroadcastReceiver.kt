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
import com.personalization.errors.ActionsError
import com.personalization.errors.EmptyFieldError
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

        when (action) {
            ACTION_NEXT_IMAGE, ACTION_PREVIOUS_IMAGE -> {
                when {
                    !images.isNullOrEmpty() && !title.isNullOrEmpty() && !body.isNullOrEmpty() -> {
                        val inputData = Data.Builder()
                            .putString(NOTIFICATION_IMAGES, images)
                            .putString(NOTIFICATION_TITLE, title)
                            .putString(NOTIFICATION_BODY, body)
                            .putInt(CURRENT_IMAGE_INDEX, currentIndex)
                            .build()

                        val updateNotificationWork =
                            OneTimeWorkRequestBuilder<UpdateNotificationWorker>()
                                .setInputData(inputData)
                                .setConstraints(
                                    Constraints.Builder().setRequiredNetworkType(
                                        networkType = NetworkType.UNMETERED
                                    ).build()
                                )
                                .build()

                        WorkManager.getInstance(context).enqueueUniqueWork(
                            /* uniqueWorkName = */ UPDATE_WORK,
                            /* existingWorkPolicy = */ ExistingWorkPolicy.APPEND_OR_REPLACE,
                            /* work = */ updateNotificationWork
                        )
                    }

                    else -> EmptyFieldError(
                        tag = TAG,
                        functionName = ON_RECEIVE,
                        message = "One of the fields is empty or null"
                    ).logError()
                }
            }

            else -> ActionsError(
                tag = TAG,
                functionName = ON_RECEIVE,
                actionName = action.orEmpty(),
                message = "Due to unknown action"
            ).logError()
        }
    }

    companion object {
        private const val TAG = "NotificationBroadcastReceiver"
        private const val ON_RECEIVE = "OnReceive"
        private const val UPDATE_WORK = "update_notification_work"
    }
}
