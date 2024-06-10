package com.personalizatio.sample

import android.app.Application
import android.util.Log
import com.personalizatio.SDK
import com.personalizatio.notification.NotificationHelper
import com.personalizatio.notification.NotificationHelper.createNotification
import com.personalizatio.notification.NotificationHelper.loadBitmaps
import java.util.concurrent.Executors
import kotlin.reflect.KClass
import kotlin.reflect.safeCast

abstract class AbstractSampleApplication<out T : SDK> internal constructor(
    private val classT: KClass<T>
) : Application() {
    protected abstract val shopId: String?
        get
    private val executorService = Executors.newFixedThreadPool(4)

    protected abstract fun initialize()

    override fun onCreate() {
        super.onCreate()

        val sdk = classT.safeCast(SDK)

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
