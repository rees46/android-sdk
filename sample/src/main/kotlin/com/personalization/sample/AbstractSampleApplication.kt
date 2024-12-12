package com.personalization.sample

import android.app.Application
import android.util.Log
import com.personalization.SDK
import com.personalization.features.notification.domain.model.NotificationData
import com.personalization.features.notification.presentation.helpers.NotificationImageHelper.loadBitmaps
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
                    loadBitmaps(urls = data.images)
                }
                sdk.notificationHelper.createNotification(
                    context = applicationContext,
                    data = NotificationData(
                        title = data.title,
                        body = data.body,
                        images = data.images
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
