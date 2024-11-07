package com.rees46.sdk

import android.content.Context
import com.personalization.BuildConfig
import com.personalization.SDK
import com.personalization.notification.core.NotificationHelper
import com.personalization.notification.helpers.NotificationImageHelper
import com.personalization.notification.model.NotificationConstants.NOTIFICATION_BODY
import com.personalization.notification.model.NotificationConstants.NOTIFICATION_IMAGES
import com.personalization.notification.model.NotificationConstants.NOTIFICATION_TITLE
import com.personalization.notification.model.NotificationData
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
        private const val NOTIFICATION_TYPE = "REES46_NOTIFICATION_TYPE"
        private const val NOTIFICATION_ID = "REES46_NOTIFICATION_ID"

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
            shopSecretKey: String,
            apiHost: String? = null,
            autoSendPushToken: Boolean = true
        ) {
            val apiUrl = apiHost?.let { "https://$it/" } ?: API_URL

            val sdk = getInstance()

            sdk.initialize(
                context = context,
                shopSecretKey = shopSecretKey,
                shopId = shopId,
                apiUrl = apiUrl,
                tag = TAG,
                preferencesKey = PREFERENCES_KEY,
                stream = PLATFORM_ANDROID,
                notificationType = NOTIFICATION_TYPE,
                notificationId = NOTIFICATION_ID,
                autoSendPushToken = autoSendPushToken
            )

            sdk.setOnMessageListener { data: Map<String, String> ->
                coroutineScope.launch {
                    val images = withContext(Dispatchers.IO) {
                        NotificationImageHelper.loadBitmaps(urls = data[NOTIFICATION_IMAGES])
                    }
                    NotificationHelper.createNotification(
                        context = context,
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
    }
}
