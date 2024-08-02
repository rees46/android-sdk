package com.personalization.di

import com.personalization.SDK
import com.personalization.data.di.RepositoriesModule
import com.personalization.data.di.DataSourcesModule
import com.personalization.data.di.ModelsModule
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
