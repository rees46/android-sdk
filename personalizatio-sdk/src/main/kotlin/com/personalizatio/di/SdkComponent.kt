package com.personalizatio.di

import com.personalizatio.SDK
import com.personalizatio.data.di.DataModule
import com.personalizatio.data.di.DataSourceModule
import dagger.Component
import javax.inject.Singleton

@Singleton
@Component(
    modules = [DataSourceModule::class, DataModule::class, SdkModule::class]
)
interface SdkComponent {

    @Component.Factory
    interface Factory {
        fun create(): SdkComponent
    }

    fun inject(sdk: SDK)
}
