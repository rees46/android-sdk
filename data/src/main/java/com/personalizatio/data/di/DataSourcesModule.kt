package com.personalizatio.data.di

import com.personalizatio.data.repositories.notification.NotificationDataSource
import com.personalizatio.data.repositories.preferences.PreferencesDataSource
import com.personalizatio.data.repositories.recommendation.RecommendationDataSource
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
