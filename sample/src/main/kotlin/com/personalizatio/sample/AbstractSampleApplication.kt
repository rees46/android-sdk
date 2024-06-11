package com.personalizatio.sample

import android.app.Application
import android.util.Log
import com.personalizatio.SDK
import java.io.IOException
import java.net.URL

abstract class AbstractSampleApplication<out T : SDK> internal constructor(
    private val sdk: SDK
): Application() {
    protected abstract val shopId: String?
        get
    private val executorService = Executors.newFixedThreadPool(4)

    protected abstract fun initialize()

    override fun onCreate() {
        super.onCreate()

        //Demo shop
        initialize()
        sdk?.getSid { sid -> Log.d("APP", "sid: $sid") }
        sdk?.setOnMessageListener { data: Map<String, String> ->
            executorService.submit {
                createNotification(
                    context = applicationContext,
                    data = data,
                    images = loadBitmaps(
                        urls = data[NotificationHelper.NOTIFICATION_IMAGES]
                    ),
                    currentIndex = 0
                )
            }
        }
    }
}
