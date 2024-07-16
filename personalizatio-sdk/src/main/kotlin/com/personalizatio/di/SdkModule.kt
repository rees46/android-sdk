package com.personalizatio.di

import com.personalizatio.RegisterManager
import com.personalizatio.api.managers.NetworkManager
import com.personalizatio.api.managers.RecommendationManager
import com.personalizatio.api.managers.TrackEventManager
import com.personalizatio.features.recommendation.RecommendationManagerImpl
import com.personalizatio.features.track_event.TrackEventManagerImpl
import com.personalizatio.network.NetworkManagerImpl
import com.personalizatio.stories.StoriesManager
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

    @Singleton
    @Provides
    fun provideRecommendationManager(): RecommendationManager {
        return RecommendationManagerImpl()
    }

    @Singleton
    @Provides
    fun provideTrackEventManager(): TrackEventManager {
        return TrackEventManagerImpl()
    }

    @Singleton
    @Provides
    fun provideStoriesManager(): StoriesManager {
        return StoriesManager()
    }
}