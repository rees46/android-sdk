package com.rees46.sdk

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.firebase.messaging.FirebaseMessaging
import com.personalizatio.BuildConfig
import com.personalizatio.SDK
import com.personalizatio.notification.NotificationHelper
import kotlinx.coroutines.*

class REES46 private constructor() : SDK() {

    companion object {

        private const val TAG: String = "REES46"
        private const val PREFERENCES_KEY: String = "rees46.sdk"
        private const val PLATFORM_ANDROID: String = "android"
        private val API_URL: String = when {
            BuildConfig.DEBUG -> "http://dev.api.rees46.com:8000/"
            else -> "https://api.rees46.ru/"
        }

        fun getInstance() : SDK = instance

        /**
         * Initialize api
         * @param context application context
         * @param shopId Shop key
         */
        @OptIn(DelicateCoroutinesApi::class)
        fun initialize(context: Context, shopId: String, apiHost: String? = null) {
            val apiUrl = apiHost?.let { "https://$it/" } ?: API_URL

            val sdk = getInstance()
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task: Task<String> ->
                println("****DETEKT LOG**** : ${task.result }")
            }
            sdk.initialize(
                context = context,
                shopId = shopId,
                apiUrl = apiUrl,
                tag = TAG,
                preferencesKey = PREFERENCES_KEY,
                stream = PLATFORM_ANDROID
            )
            // Default message equipment without customization
            sdk.setOnMessageListener { data: Map<String, String> ->
                GlobalScope.launch(Dispatchers.Main) {
                    val images = withContext(Dispatchers.IO) {
                        NotificationHelper.loadBitmaps(urls = data[NotificationHelper.NOTIFICATION_IMAGES])
                    }
                    NotificationHelper.createNotification(
                        context = context,
                        data = data,
                        images = images,
                        currentIndex = 0
                    )
                }
            }
        }
    }
}
