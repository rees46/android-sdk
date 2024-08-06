package com.personalization.data.di

import com.personalization.data.repositories.notification.NotificationRepositoryImpl
import com.personalization.data.repositories.preferences.PreferencesRepositoryImpl
import com.personalization.data.repositories.recommendation.RecommendationRepositoryImpl
import com.personalization.domain.repositories.PreferencesRepository
import com.personalization.domain.repositories.RecommendationRepository
import com.personalization.domain.repositories.NotificationRepository
import dagger.Binds
import dagger.Module

@Module
abstract class RepositoriesModule {

    @Binds
    abstract fun bindPreferencesRepository(impl: PreferencesRepositoryImpl): PreferencesRepository

    @Binds
    abstract fun bindRecommendationRepository(impl: RecommendationRepositoryImpl): RecommendationRepository

    @Binds
    abstract fun bindNotificationRepository(impl: NotificationRepositoryImpl): NotificationRepository
}
