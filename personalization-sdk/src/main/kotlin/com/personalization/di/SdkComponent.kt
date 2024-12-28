package com.personalization.di

import com.personalization.SDK
import com.personalization.features.notification.service.NotificationService
import com.personalization.sdk.data.di.DataSourcesModule
import com.personalization.sdk.data.di.ModelsModule
import com.personalization.sdk.data.di.RepositoriesModule
import dagger.Component
import javax.inject.Singleton

@Singleton
@Component(
    modules = [
        DataSourcesModule::class,
        RepositoriesModule::class,
        ModelsModule::class,
        SdkModule::class,
        AppModule::class
    ]
)
interface SdkComponent {

    @Component.Factory
    interface Factory {
        fun create(
            appModule: AppModule
        ): SdkComponent
    }

    fun inject(sdk: SDK)

    fun inject(service: NotificationService)
}
