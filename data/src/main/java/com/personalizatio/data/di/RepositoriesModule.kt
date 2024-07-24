package com.personalizatio.data.di

import com.personalizatio.data.repositories.notification.NotificationRepositoryImpl
import com.personalizatio.data.repositories.preferences.PreferencesRepositoryImpl
import com.personalizatio.data.repositories.recommendation.RecommendationRepositoryImpl
import com.personalizatio.domain.repositories.PreferencesRepository
import com.personalizatio.domain.repositories.RecommendationRepository
import com.personalizatio.domain.repositories.NotificationRepository
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
