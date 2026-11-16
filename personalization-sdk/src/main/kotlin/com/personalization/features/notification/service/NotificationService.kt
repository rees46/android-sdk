package com.personalization.features.notification.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.widget.Toast
import com.personalization.di.AppModule
import com.personalization.di.DaggerSdkComponent
import com.personalization.features.notification.domain.model.NotificationConstants.CURRENT_IMAGE_INDEX
import com.personalization.features.notification.presentation.helpers.NotificationHelper
import com.personalization.features.notification.presentation.helpers.NotificationImageHelper
import com.personalization.features.notification.service.mapper.toNotificationData
import com.personalization.resources.NotificationResources.NOTIFICATION_LOADING_DATA_ERROR
import com.personalization.resources.NotificationResources.NOTIFICATION_LOADING_IMAGE_ERROR
import com.personalization.sdk.data.models.dto.notification.NotificationData
import com.personalization.LogLevel
import com.personalization.logs.LogCodes
import com.personalization.logs.SdkLogs
import com.personalization.push.PushExtras
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
                startId = startId,
                // The push behind it, with its shop, on the new core: the dismissal is still reported.
                push = PushExtras.read(intent),
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
        startId: Int,
        push: Map<String, String>?,
    ) {
        serviceScope.launch {
            try {
                val (loadedImages, hasError) = NotificationImageHelper.loadBitmaps(
                    context = applicationContext,
                    urls = data.image
                )
                notificationHelper.redraw(
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
                    hasError = hasError,
                    push = push,
                )
            } catch (ioException: IOException) {
                showToast(
                    message = applicationContext.getString(
                        /* resId = */ NOTIFICATION_LOADING_IMAGE_ERROR
                    )
                )
                // The class only: the text names the image's URL.
                SdkLogs.log(LogLevel.WARNING, LogCodes.PUSH, "A notification image failed to load", throwable = ioException)
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
