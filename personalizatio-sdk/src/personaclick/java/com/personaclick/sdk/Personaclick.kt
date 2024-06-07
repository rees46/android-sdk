package com.personaclick.sdk

import android.content.Context
import com.personalizatio.BuildConfig
import com.personalizatio.SDK

class Personaclick private constructor() : SDK() {

    companion object {
        const val TAG: String = "PERSONACLICK"
        const val NOTIFICATION_TYPE: String = "PERSONACLICK_NOTIFICATION_TYPE"
        const val NOTIFICATION_ID: String = "PERSONACLICK_NOTIFICATION_ID"
        protected const val PREFERENCES_KEY: String = "personaclick.sdk"
        protected val API_URL: String = if (BuildConfig.DEBUG) "http://192.168.1.8:8080/" else "https://api.personaclick.com/"

        /**
         * Initialize api
         * @param context application context
         * @param shopId Shop key
         */
        fun initialize(context: Context, shopId: String) {
            if (!isInstanced()) {
                getInstance().initialize(context, shopId, API_URL, TAG, PREFERENCES_KEY, "android")
            }
        }
    }
}
