package com.personalizatio.data.di

import com.personalizatio.data.repository.notification.SourceRepository
import com.personalizatio.data.repository.preferences.PreferencesRepository
import com.personalizatio.data.repository.recommendation.RecommendationRepository
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class DataModule {

    @Singleton
    @Provides
    fun providePreferencesRepository(): PreferencesRepository {
        return PreferencesRepository()
    }

    @Singleton
    @Provides
    fun provideRecommendationRepository(): RecommendationRepository {
        return RecommendationRepository()
    }

    @Singleton
    @Provides
    fun provideSourceRepository(preferencesRepository: PreferencesRepository): SourceRepository {
        return SourceRepository(preferencesRepository)
    }
}