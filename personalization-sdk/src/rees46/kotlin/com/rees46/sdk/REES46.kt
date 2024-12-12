package com.rees46.sdk

import android.content.Context
import com.personalization.BuildConfig
import com.personalization.SDK
import com.personalization.features.notification.domain.model.NotificationData
import com.personalization.features.notification.presentation.helpers.NotificationImageHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class REES46 private constructor() : SDK() {

    companion object {

        private const val TAG: String = "REES46"
        private const val DEBUG_API_URL: String = "http://dev.api.rees46.com:8000/"
        private const val RELEASE_API_URL: String = "https://api.rees46.ru/"
        private const val PREFERENCES_KEY: String = "rees46.sdk"
        private const val PLATFORM_ANDROID: String = "android"

        private val API_URL: String = when {
            BuildConfig.DEBUG -> DEBUG_API_URL
            else -> RELEASE_API_URL
        }
        private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())

        fun getInstance(): SDK = instance

        /**
         * Initialize api
         * @param context application context
         * @param shopId Shop key
         */
        fun initialize(
            context: Context,
            shopId: String,
            apiHost: String? = null,
            autoSendPushToken: Boolean = true
        ) {
            val apiUrl = apiHost?.let { "https://$it/" } ?: API_URL

            val sdk = getInstance()

            sdk.initialize(
                context = context,
                shopId = shopId,
                apiUrl = apiUrl,
                tag = TAG,
                preferencesKey = PREFERENCES_KEY,
                stream = PLATFORM_ANDROID,
                autoSendPushToken = autoSendPushToken,
                needReInitialization = true
            )

            sdk.setOnMessageListener { data ->
                coroutineScope.launch {
                    val (images, hasError) = withContext(Dispatchers.IO) {
                        NotificationImageHelper.loadBitmaps(urls = data.images)
                    }
                    sdk.notificationHelper.createNotification(
                        context = context,
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
    }
}
