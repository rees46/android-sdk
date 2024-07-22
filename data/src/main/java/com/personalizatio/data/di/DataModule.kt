package com.personalizatio.data.di

import com.personalizatio.data.repositories.notification.SourceRepositoryImpl
import com.personalizatio.data.repositories.preferences.PreferencesRepositoryImpl
import com.personalizatio.data.repositories.recommendation.RecommendationRepositoryImpl
import com.personalizatio.domain.repositories.PreferencesRepository
import com.personalizatio.domain.repositories.RecommendationRepository
import com.personalizatio.domain.repositories.SourceRepository
import dagger.Binds
import dagger.Module
import javax.inject.Singleton

@Module
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun preferencesRepository(impl: PreferencesRepositoryImpl): PreferencesRepository

    @Binds
    @Singleton
    abstract fun recommendationRepository(impl: RecommendationRepositoryImpl): RecommendationRepository

    @Binds
    @Singleton
    abstract fun sourceRepository(impl: SourceRepositoryImpl): SourceRepository
}
