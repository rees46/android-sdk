package com.personalizatio.di

import com.personalizatio.RegisterManager
import com.personalizatio.SDK
import com.personalizatio.api.managers.NetworkManager
import com.personalizatio.data.di.DataModule
import com.personalizatio.domain.features.preferences.di.PreferencesComponent
import dagger.Component
import javax.inject.Singleton

@Singleton
@Component(
    modules = [DataModule::class, SdkModule::class]
)
interface SdkComponent {

    @Component.Factory
    interface Factory {
        fun create(): SdkComponent
    }

    fun preferencesComponent(): PreferencesComponent.Factory

    fun inject(sdk: SDK)
    fun inject(registerManager: RegisterManager)
    fun inject(networkManager: NetworkManager)
}
