package com.rees46.sdk

import android.content.Context
import com.personalizatio.BuildConfig
import com.personalizatio.SDK
import com.personalizatio.notification.NotificationHelper
import java.util.concurrent.Executors

class REES46 private constructor() : SDK() {

    companion object {
        const val TAG: String = "REES46"

        protected const val PREFERENCES_KEY: String = "rees46.sdk"
        protected val API_URL: String = when {
            BuildConfig.DEBUG -> "http://dev.api.rees46.com:8000/"
            else -> "https://api.rees46.ru/"
        }
        private val executorService = Executors.newFixedThreadPool(4)

        /**
         * Initialize api
         * @param context application context
         * @param shopId Shop key
         */
        fun initialize(context: Context, shopId: String, apiHost: String?) {
            if (!isInstanced()) {
                val apiUrl = apiHost?.let { "https://$it/" } ?: API_URL
                getInstance().initialize(context, shopId, apiUrl, TAG, PREFERENCES_KEY, "android")
            }

            // Дефолтное отображение сообщения без кастомизации
            getInstance().setOnMessageListener { data: Map<String, String> ->
                executorService.submit {
                    NotificationHelper.createNotification(
                        context = context,
                        data = data,
                        images = NotificationHelper.loadBitmaps(
                            urls = data[NotificationHelper.NOTIFICATION_IMAGES]
                        ),
                        currentIndex = 0
                    )
                }
            }
        }
    }
}
