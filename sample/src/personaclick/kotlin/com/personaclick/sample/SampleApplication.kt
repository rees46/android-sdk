package com.personaclick.sample

import com.personaclick.sdk.Personaclick
import com.personalization.sample.AbstractSampleApplication

class SampleApplication : AbstractSampleApplication<Personaclick>(Personaclick.getInstance()) {
    override val shopId: String
        get() = "357382bf66ac0ce2f1722677c59511"
    override val shopSecretKey: String
        get() = "9902b2c111e006ee944833c95e9e6066"

    override fun initialize() {
        Personaclick.initialize(
            context = applicationContext,
            shopId = shopId,
            shopSecretKey = shopSecretKey
        )
    }
}
