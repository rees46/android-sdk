package com.personaclick.sdk

import android.content.Context
import com.personalization.BuildConfig
import com.personalization.SDK

class Personaclick private constructor() : SDK() {

    companion object {
        private val DOMAIN_API: String = when {
            BuildConfig.DEBUG -> "192.168.1.8:8080"
            else -> "api.personaclick.com"
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
            apiHost: String? = null
        ) {
            val sdk = getInstance()

            initSdk(
                sdk = sdk,
                context = context,
                shopId = shopId,
                apiHost = apiHost
            )
        }

        private fun initSdk(sdk: SDK, context: Context, shopId: String, apiHost: String?) {
            sdk.initialize(
                context = context,
                shopId = shopId,
                apiDomain = apiHost ?: DOMAIN_API
            )
        }
    }
}
