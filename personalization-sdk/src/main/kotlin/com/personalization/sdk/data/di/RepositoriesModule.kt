package com.personalization.sdk.data.di

import com.personalization.sdk.data.repositories.network.NetworkRepositoryImpl
import com.personalization.sdk.data.repositories.notification.NotificationRepositoryImpl
import com.personalization.sdk.data.repositories.preferences.PreferencesRepositoryImpl
import com.personalization.sdk.data.repositories.recommendation.RecommendationRepositoryImpl
import com.personalization.sdk.domain.repositories.NetworkRepository
import com.personalization.sdk.domain.repositories.NotificationRepository
import com.personalization.sdk.domain.repositories.PreferencesRepository
import com.personalization.sdk.domain.repositories.RecommendationRepository
import dagger.Binds
import dagger.Module

@Module
abstract class RepositoriesModule {

    @Binds
    abstract fun bindPreferencesRepository(impl: PreferencesRepositoryImpl): PreferencesRepository

    @Binds
    abstract fun bindNetworkRepository(impl: NetworkRepositoryImpl): NetworkRepository

    @Binds
    abstract fun bindRecommendationRepository(impl: RecommendationRepositoryImpl): RecommendationRepository

    @Binds
    abstract fun bindNotificationRepository(impl: NotificationRepositoryImpl): NotificationRepository
}
