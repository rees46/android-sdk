package com.personalizatio.di

import com.personalizatio.RegisterManager
import com.personalizatio.api.managers.NetworkManager
import com.personalizatio.api.managers.RecommendationManager
import com.personalizatio.api.managers.SearchManager
import com.personalizatio.api.managers.TrackEventManager
import com.personalizatio.domain.usecases.notification.GetNotificationSourceUseCase
import com.personalizatio.domain.usecases.preferences.GetPreferencesValueUseCase
import com.personalizatio.domain.usecases.preferences.SavePreferencesValueUseCase
import com.personalizatio.domain.usecases.recommendation.GetRecommendedByUseCase
import com.personalizatio.domain.usecases.recommendation.SetRecommendedByUseCase
import com.personalizatio.features.recommendation.RecommendationManagerImpl
import com.personalizatio.features.search.SearchManagerImpl
import com.personalizatio.features.track_event.TrackEventManagerImpl
import com.personalizatio.network.NetworkManagerImpl
import com.personalizatio.stories.StoriesManager
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
