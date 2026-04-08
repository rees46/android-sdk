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
         * Returns the SDK instance registered for the given shopId, or null if not found.
         */
        fun getInstance(shopId: String): SDK? = SDK.getInstance(shopId)

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

        /**
         * Creates and initializes a new SDK instance for the given shopId.
         * Use this for multi-instance scenarios where each shop needs its own SDK.
         *
         * @param context application context
         * @param shopId Shop key
         * @param apiHost optional API host override
         * @return initialized SDK instance
         */
        fun initializeInstance(
            context: Context,
            shopId: String,
            apiHost: String? = null
        ): SDK {
            val sdk = SDK()

            initSdk(
                sdk = sdk,
                context = context,
                shopId = shopId,
                apiHost = apiHost
            )

            return sdk
        }

        private fun initSdk(
            sdk: SDK,
            context: Context,
            shopId: String,
            apiHost: String?
        ) = sdk.initialize(
            context = context,
            shopId = shopId,
            apiDomain = apiHost ?: DOMAIN_API
        )
    }
}
