package com.personalization.features.notification.data.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.personalization.features.notification.domain.model.NotificationConstants.CURRENT_IMAGE_INDEX
import com.personalization.features.notification.domain.model.NotificationConstants.NOTIFICATION_BODY
import com.personalization.features.notification.domain.model.NotificationConstants.NOTIFICATION_IMAGES
import com.personalization.features.notification.domain.model.NotificationConstants.NOTIFICATION_TITLE
import com.personalization.features.notification.domain.model.NotificationData
import com.personalization.features.notification.presentation.helpers.NotificationHelper
import com.personalization.features.notification.presentation.helpers.NotificationImageHelper
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class NotificationUpdateService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val currentIndex = intent?.getIntExtra(CURRENT_IMAGE_INDEX, 0) ?: 0
        val images = intent?.getStringExtra(NOTIFICATION_IMAGES)
        val title = intent?.getStringExtra(NOTIFICATION_TITLE)
        val body = intent?.getStringExtra(NOTIFICATION_BODY)

        if (!images.isNullOrEmpty() && !title.isNullOrEmpty() && !body.isNullOrEmpty()) {
            serviceScope.launch {
                try {
                    val loadedImages = NotificationImageHelper.loadBitmaps(urls = images)

                    NotificationHelper.createNotification(
                        context = this@NotificationUpdateService,
                        notificationId = (title + body).hashCode(),
                        data = NotificationData(title = title, body = body, images = images),
                        images = loadedImages,
                        currentImageIndex = currentIndex
                    )
                } catch (ioException: IOException) {
                    ioException.printStackTrace()
                } finally {
                    stopSelf(startId)
                }
            }
        } else {
            stopSelf(startId)
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
}
