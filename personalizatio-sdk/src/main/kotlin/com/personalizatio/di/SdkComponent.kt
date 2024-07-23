package com.personalizatio.di

import com.personalizatio.SDK
import com.personalizatio.data.di.RepositoriesModule
import com.personalizatio.data.di.DataSourcesModule
import com.personalizatio.data.di.ModelsModule
import dagger.Component
import javax.inject.Singleton

@Singleton
@Component(
    modules = [
        DataSourcesModule::class,
        RepositoriesModule::class,
        ModelsModule::class,
        SdkModule::class
    ]
)
interface SdkComponent {

    @Component.Factory
    interface Factory {
        fun create(): SdkComponent
    }

    fun inject(sdk: SDK)
}
