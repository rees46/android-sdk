package com.personaclick.sample

import com.personaclick.sdk.Personaclick
import com.personalizatio.sample.AbstractSampleApplication

/**
 * Created by Sergey Odintsov
 *
 * @author nixx.dj@gmail.com
 */
class SampleApplication : AbstractSampleApplication<Personaclick>(Personaclick::class) {
    override val shopId: String
        get() = "cb0516af5da25b1b41490072e679bc"

    override fun initialize() {
        Personaclick.initialize(applicationContext, shopId)
    }
}
