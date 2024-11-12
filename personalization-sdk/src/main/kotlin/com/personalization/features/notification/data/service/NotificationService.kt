package com.personalization.features.notification.data.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.widget.Toast
import com.personalization.features.notification.domain.model.NotificationConstants.CURRENT_IMAGE_INDEX
import com.personalization.features.notification.domain.model.NotificationConstants.NOTIFICATION_BODY
import com.personalization.features.notification.domain.model.NotificationConstants.NOTIFICATION_IMAGES
import com.personalization.features.notification.domain.model.NotificationConstants.NOTIFICATION_TITLE
import com.personalization.features.notification.domain.model.NotificationData
import com.personalization.features.notification.presentation.helpers.NotificationHelper
import com.personalization.features.notification.presentation.helpers.NotificationImageHelper
import com.personalization.resources.NotificationResources.NOTIFICATION_LOADING_DATA_ERROR
import com.personalization.resources.NotificationResources.NOTIFICATION_LOADING_IMAGE_ERROR
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class NotificationService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(
        context = Dispatchers.IO + serviceJob
    )

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        val currentIndex = intent?.getIntExtra(CURRENT_IMAGE_INDEX, -1) ?: -1
        val images = intent?.getStringExtra(NOTIFICATION_IMAGES)
        val title = intent?.getStringExtra(NOTIFICATION_TITLE)
        val body = intent?.getStringExtra(NOTIFICATION_BODY)

        if (!isValidNotificationData(images, title, body, currentIndex)) {
            showToast(
                message = applicationContext.getString(
                    /* resId = */ NOTIFICATION_LOADING_DATA_ERROR
                )
            )
            stopSelf(startId)
            return START_NOT_STICKY
        }

        updateNotification(
            images = images,
            title = title,
            body = body,
            currentIndex = currentIndex,
            startId = startId
        )

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    private fun isValidNotificationData(
        images: String?,
        title: String?,
        body: String?,
        currentIndex: Int
    ): Boolean {
        return !images.isNullOrEmpty() && !title.isNullOrEmpty() && !body.isNullOrEmpty() && currentIndex >= 0
    }

    private fun updateNotification(
        images: String?,
        title: String?,
        body: String?,
        currentIndex: Int,
        startId: Int
    ) {
        serviceScope.launch {
            try {
                val loadedImages = NotificationImageHelper.loadBitmaps(urls = images)
                NotificationHelper.createNotification(
                    context = this@NotificationService,
                    notificationId = (title + body).hashCode(),
                    data = NotificationData(
                        title = title,
                        body = body,
                        images = images
                    ),
                    images = loadedImages,
                    currentImageIndex = currentIndex
                )
            } catch (ioException: IOException) {
                showToast(
                    message = applicationContext.getString(
                        /* resId = */ NOTIFICATION_LOADING_IMAGE_ERROR
                    )
                )
                ioException.printStackTrace()
            } finally {
                stopSelf(startId)
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(
            /* context = */ applicationContext,
            /* text = */ message,
            /* duration = */ Toast.LENGTH_SHORT
        ).show()
    }
}