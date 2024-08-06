package com.personalization.data.di

import com.personalization.data.repositories.notification.NotificationDataSource
import com.personalization.data.repositories.preferences.PreferencesDataSource
import com.personalization.data.repositories.recommendation.RecommendationDataSource
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class DataSourcesModule {

    @Provides
    @Singleton
    fun providePreferencesDataSource() = PreferencesDataSource()

    @Provides
    @Singleton
    fun provideRecommendationDataSource() = RecommendationDataSource()

    @Provides
    fun provideNotificationDataSource(
        preferencesDataSource: PreferencesDataSource
    ): NotificationDataSource {
        return NotificationDataSource(
            preferencesDataSource = preferencesDataSource
        )
    }
}
