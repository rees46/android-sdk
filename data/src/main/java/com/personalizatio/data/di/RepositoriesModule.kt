package com.personalizatio.data.di

import com.personalizatio.data.repositories.notification.NotificationRepositoryImpl
import com.personalizatio.data.repositories.preferences.PreferencesRepositoryImpl
import com.personalizatio.data.repositories.recommendation.RecommendationRepositoryImpl
import com.personalizatio.domain.repositories.PreferencesRepository
import com.personalizatio.domain.repositories.RecommendationRepository
import com.personalizatio.domain.repositories.NotificationRepository
import dagger.Binds
import dagger.Module
import javax.inject.Singleton

@Module
abstract class RepositoriesModule {

    @Binds
    @Singleton
    abstract fun bindPreferencesRepository(impl: PreferencesRepositoryImpl): PreferencesRepository

    @Binds
    @Singleton
    abstract fun bindRecommendationRepository(impl: RecommendationRepositoryImpl): RecommendationRepository

    @Binds
    @Singleton
    abstract fun bindNotificationRepository(impl: NotificationRepositoryImpl): NotificationRepository
}
