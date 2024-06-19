package com.personaclick.sdk

import android.content.Context
import com.personalizatio.BuildConfig
import com.personalizatio.SDK

class Personaclick private constructor() : SDK() {

    companion object {
        private const val TAG: String = "PERSONACLICK"
        private const val PREFERENCES_KEY: String = "personaclick.sdk"
        private const val PLATFORM_ANDROID: String = "android"
        private val API_URL: String = when {
            BuildConfig.DEBUG -> "http://192.168.1.8:8080/"
            else -> "https://api.personaclick.com/"
        }
        const val NOTIFICATION_TYPE: String = "PERSONACLICK_NOTIFICATION_TYPE"
        const val NOTIFICATION_ID: String = "PERSONACLICK_NOTIFICATION_ID"

        fun getInstance() : SDK = instance

        /**
         * Initialize api
         * @param context application context
         * @param shopId Shop key
         */
        fun initialize(context: Context, shopId: String, autoSendPushToken: Boolean = true) {
            val sdk = getInstance()
            sdk.initialize(
                context = context,
                shopId = shopId,
                apiUrl = API_URL,
                tag = TAG,
                preferencesKey = PREFERENCES_KEY,
                stream = PLATFORM_ANDROID,
                autoSendPushToken = autoSendPushToken
            )
        }
    }
}
