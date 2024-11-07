package com.personalization.sample

import android.app.Application
import android.util.Log
import com.personalization.SDK
import com.personalization.notification.core.NotificationHelper.createNotification
import com.personalization.notification.helpers.NotificationImageHelper.loadBitmaps
import com.personalization.notification.model.NotificationConstants.NOTIFICATION_BODY
import com.personalization.notification.model.NotificationConstants.NOTIFICATION_IMAGES
import com.personalization.notification.model.NotificationConstants.NOTIFICATION_TITLE
import com.personalization.notification.model.NotificationData
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
    protected abstract val shopSecretKey: String

    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())

    protected abstract fun initialize()

    override fun onCreate() {
        super.onCreate()

        // Demo shop
        initialize()
        sdk.getSid { sid -> Log.d("APP", "sid: $sid") }
        sdk.setOnMessageListener { data: Map<String, String> ->
            coroutineScope.launch {
                val images = withContext(Dispatchers.IO) {
                    loadBitmaps(urls = data[NOTIFICATION_IMAGES])
                }
                createNotification(
                    context = applicationContext,
                    notificationId = data.hashCode(),
                    data = NotificationData(
                        title = data[NOTIFICATION_TITLE],
                        body = data[NOTIFICATION_BODY],
                        images = data[NOTIFICATION_IMAGES]
                    ),
                    images = images
                )
            }
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        coroutineScope.cancel()
    }
}
