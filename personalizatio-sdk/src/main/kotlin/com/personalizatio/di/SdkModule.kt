package com.personalizatio.di

import com.personalizatio.RegisterManager
import com.personalizatio.api.managers.NetworkManager
import com.personalizatio.network.NetworkManagerImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class SdkModule {

    @Singleton
    @Provides
    fun provideRegisterManager(): RegisterManager {
        return RegisterManager()
    }

    @Singleton
    @Provides
    fun provideNetworkManager(registerManager: RegisterManager): NetworkManager {
        return NetworkManagerImpl(registerManager)
    }
}