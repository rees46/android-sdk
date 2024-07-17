package com.personalizatio.di

import com.personalizatio.RegisterManager
import com.personalizatio.api.managers.NetworkManager
import com.personalizatio.api.managers.RecommendationManager
import com.personalizatio.api.managers.SearchManager
import com.personalizatio.api.managers.TrackEventManager
import com.personalizatio.domain.features.recommendation.usecase.GetRecommendedByUseCase
import com.personalizatio.domain.features.recommendation.usecase.SetRecommendedByUseCase
import com.personalizatio.features.recommendation.RecommendationManagerImpl
import com.personalizatio.features.search.SearchManagerImpl
import com.personalizatio.features.track_event.TrackEventManagerImpl
import com.personalizatio.network.NetworkManagerImpl
import com.personalizatio.stories.StoriesManager
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
