package com.rees46.sample

import com.personalization.BuildConfig
import com.personalization.sample.AbstractSampleApplication
import com.rees46.sdk.REES46

class SampleApplication : AbstractSampleApplication<REES46>(REES46.getInstance()) {

    override val shopId: String
        get() = when {
            BuildConfig.DEBUG -> DEBUG_SHOP_IP
            else -> RELEASE_SHOP_IP
        }

    override val shopSecretKey: String
        get() = when {
            BuildConfig.DEBUG -> DEBUG_SHOP_SECRET_KEY
            else -> RELEASE_SHOP_SECRET_KEY
        }

    override fun initialize() {
        REES46.initialize(
            context = applicationContext,
            shopId = shopId,
            shopSecretKey = shopSecretKey,
            apiHost = null
        )
    }

    companion object {
        private const val DEBUG_SHOP_IP = "cb0516af5da25b1b41490072e679bc"
        private const val RELEASE_SHOP_IP = "357382bf66ac0ce2f1722677c59511"
        private const val DEBUG_SHOP_SECRET_KEY = "secret_key"
        private const val RELEASE_SHOP_SECRET_KEY = "secret_key"
    }
}
