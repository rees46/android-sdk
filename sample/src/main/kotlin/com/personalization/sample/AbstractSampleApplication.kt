package com.personalization.sample

import android.app.Application
import android.util.Log
import com.personalization.SDK
import com.personalization.features.notification.presentation.helpers.NotificationImageHelper.loadBitmaps
import com.personalization.sdk.data.models.dto.notification.NotificationData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

abstract class AbstractSampleApplication<out T : SDK> internal constructor(
    private val sdk: SDK
) : Application() {
    protected abstract val shopId: String

    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())

    protected abstract fun initialize()

    override fun onCreate() {
        super.onCreate()

        initialize()
        sdk.getSid { sid -> Log.d("APP", "sid: $sid") }

        sdk.setOnMessageListener { data ->
            coroutineScope.launch {
                val (images, hasError) = withContext(Dispatchers.IO) {
                    loadBitmaps(urls = data.image)
                }
                sdk.notificationHelper.createNotification(
                    context = applicationContext,
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
                    images = images,
                    hasError = hasError
                )
            }
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        coroutineScope.cancel()
    }
}
