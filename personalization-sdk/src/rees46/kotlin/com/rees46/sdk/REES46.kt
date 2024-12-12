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

        private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())

        private val API_URL: String = when {
            BuildConfig.DEBUG -> "http://dev.api.rees46.com:8000/"
            else -> "https://api.rees46.ru/"
        }

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
        ) {
            val sdk = getInstance()

            initSdk(
                sdk = sdk,
                context = context,
                shopId = shopId,
                apiHost = apiHost
            )

            performNotification(
                sdk = sdk,
                context = context
            )
        }

        private fun initSdk(sdk: SDK, context: Context, shopId: String, apiHost: String?) {
            sdk.initialize(
                context = context,
                shopId = shopId,
                apiUrl = apiHost?.let { "https://$it/" } ?: API_URL
            )
        }

        private fun performNotification(sdk: SDK, context: Context) {
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
