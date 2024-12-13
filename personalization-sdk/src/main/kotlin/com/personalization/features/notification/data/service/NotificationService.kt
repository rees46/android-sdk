package com.personalization.features.notification.data.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.widget.Toast
import com.personalization.di.AppModule
import com.personalization.di.DaggerSdkComponent
import com.personalization.features.notification.data.mapper.toNotificationData
import com.personalization.features.notification.domain.model.NotificationConstants.CURRENT_IMAGE_INDEX
import com.personalization.features.notification.domain.model.NotificationData
import com.personalization.features.notification.presentation.helpers.NotificationHelper
import com.personalization.features.notification.presentation.helpers.NotificationImageHelper
import com.personalization.resources.NotificationResources.NOTIFICATION_LOADING_DATA_ERROR
import com.personalization.resources.NotificationResources.NOTIFICATION_LOADING_IMAGE_ERROR
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class NotificationService : Service() {

    @Inject
    lateinit var notificationHelper: NotificationHelper

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(
        context = Dispatchers.IO + serviceJob
    )

    override fun onCreate() {
        super.onCreate()
        DaggerSdkComponent.factory().create(
            appModule = AppModule(
                applicationContext = applicationContext
            )
        ).inject(service = this)
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        val currentIndex = intent?.getIntExtra(CURRENT_IMAGE_INDEX, -1) ?: -1
        val data: NotificationData? = intent?.toNotificationData()

        if (!isValidNotificationData(data?.image, data?.title, data?.body, currentIndex)) {
            return onStopService(startId = startId)
        }

        when {
            data != null -> updateNotification(
                data,
                currentIndex = currentIndex,
                startId = startId
            )

            else -> onStopService(startId)
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun onStopService(startId: Int): Int {
        showToast(
            message = applicationContext.getString(
                /* resId = */ NOTIFICATION_LOADING_DATA_ERROR
            )
        )
        stopSelf(startId)
        return START_NOT_STICKY
    }

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
        data: NotificationData,
        currentIndex: Int,
        startId: Int
    ) {
        serviceScope.launch {
            try {
                val (loadedImages, hasError) = NotificationImageHelper.loadBitmaps(urls = data.image)
                notificationHelper.createNotification(
                    context = this@NotificationService,
                    data = NotificationData(
                        id = data.id,
                        title = data.title,
                        body = data.body,
                        icon = data.icon,
                        type = data.type,
                        actions = data.actions,
                        actionUrls = data.actionUrls,
                        image = data.image,
                        event = data.event,
                    ),
                    images = loadedImages,
                    currentImageIndex = currentIndex,
                    hasError = hasError
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
