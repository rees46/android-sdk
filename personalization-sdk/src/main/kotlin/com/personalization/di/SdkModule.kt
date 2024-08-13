package com.personalization.di

import com.personalization.RegisterManager
import com.personalization.api.managers.NetworkManager
import com.personalization.api.managers.RecommendationManager
import com.personalization.api.managers.SearchManager
import com.personalization.api.managers.TrackEventManager
import com.personalization.sdk.domain.usecases.notification.GetNotificationSourceUseCase
import com.personalization.sdk.domain.usecases.preferences.GetPreferencesValueUseCase
import com.personalization.sdk.domain.usecases.preferences.SavePreferencesValueUseCase
import com.personalization.sdk.domain.usecases.recommendation.GetRecommendedByUseCase
import com.personalization.sdk.domain.usecases.recommendation.SetRecommendedByUseCase
import com.personalization.features.recommendation.RecommendationManagerImpl
import com.personalization.features.search.SearchManagerImpl
import com.personalization.features.track_event.TrackEventManagerImpl
import com.personalization.network.NetworkManagerImpl
import com.personalization.stories.StoriesManager
import dagger.Lazy
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class SdkModule {

    @Singleton
    @Provides
    fun provideRegisterManager(
        getPreferencesValueUseCase: GetPreferencesValueUseCase,
        savePreferencesValueUseCase: SavePreferencesValueUseCase,
        networkManager: Lazy<NetworkManager>
    ): RegisterManager {
        return RegisterManager(
            getPreferencesValueUseCase = getPreferencesValueUseCase,
            savePreferencesValueUseCase = savePreferencesValueUseCase,
            networkManager = networkManager
        )
    }

    @Singleton
    @Provides
    fun provideNetworkManager(
        registerManager: RegisterManager,
        getNotificationSourceUseCase: GetNotificationSourceUseCase
    ): NetworkManager {
        return NetworkManagerImpl(
            registerManager = registerManager,
            getNotificationSourceUseCase = getNotificationSourceUseCase
        )
    }

    @Singleton
    @Provides
    fun provideRecommendationManager(networkManager: NetworkManager): RecommendationManager {
        return RecommendationManagerImpl(networkManager)
    }

    @Singleton
    @Provides
    fun provideTrackEventManager(
        networkManager: NetworkManager,
        getRecommendedByUseCase: GetRecommendedByUseCase,
        setRecommendedByUseCase: SetRecommendedByUseCase
    ): TrackEventManager {
        return TrackEventManagerImpl(
            networkManager = networkManager,
            getRecommendedByUseCase = getRecommendedByUseCase,
            setRecommendedByUseCase = setRecommendedByUseCase
        )
    }

    @Singleton
    @Provides
    fun provideStoriesManager(
        networkManager: NetworkManager,
        setRecommendedByUseCase: SetRecommendedByUseCase
    ): StoriesManager {
        return StoriesManager(
            networkManager = networkManager,
            setRecommendedByUseCase = setRecommendedByUseCase
        )
    }

    @Singleton
    @Provides
    fun provideSearchManager(networkManager: NetworkManager): SearchManager {
        return SearchManagerImpl(networkManager)
    }
}
