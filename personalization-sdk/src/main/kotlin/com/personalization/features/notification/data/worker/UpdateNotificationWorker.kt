package com.personalization.features.notification.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.personalization.SDK
import com.personalization.features.notification.domain.model.NotificationConstants.CURRENT_IMAGE_INDEX
import com.personalization.features.notification.domain.model.NotificationConstants.NOTIFICATION_BODY
import com.personalization.features.notification.domain.model.NotificationConstants.NOTIFICATION_IMAGES
import com.personalization.features.notification.domain.model.NotificationConstants.NOTIFICATION_TITLE
import com.personalization.features.notification.domain.model.NotificationData
import com.personalization.features.notification.presentation.helpers.NotificationHelper
import com.personalization.features.notification.presentation.helpers.NotificationImageHelper
import java.io.IOException

class UpdateNotificationWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val context = applicationContext

        val newIndex = inputData.getInt(CURRENT_IMAGE_INDEX, 0)
        val images = inputData.getString(NOTIFICATION_IMAGES)
        val title = inputData.getString(NOTIFICATION_TITLE)
        val body = inputData.getString(NOTIFICATION_BODY)

        return if (images.isNullOrEmpty() || title.isNullOrEmpty() || body.isNullOrEmpty()) {
            SDK.error("Invalid input data: images=$images, title=$title, body=$body")

            Result.failure()
        } else {

            val data = NotificationData(
                title = title,
                body = body,
                images = images
            )

            try {
                val loadedImages = NotificationImageHelper.loadBitmaps(urls = images)
                NotificationHelper.createNotification(
                    context = context,
                    notificationId = data.hashCode(),
                    data = data,
                    images = loadedImages,
                    currentIndex = newIndex
                )

                Result.success()
            } catch (ioException: IOException) {
                SDK.error("Error caught in updateNotification", ioException)
                Result.failure()
            }
        }
    }
}
