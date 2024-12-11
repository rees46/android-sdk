package com.personalization.sdk.data.di

import com.personalization.sdk.data.repositories.network.NetworkDataSource
import com.personalization.sdk.data.repositories.notification.NotificationDataSource
import com.personalization.sdk.data.repositories.preferences.PreferencesDataSource
import com.personalization.sdk.data.repositories.recommendation.RecommendationDataSource
import com.personalization.sdk.data.repositories.userSettings.UserSettingsDataSource
import dagger.Module
import dagger.Provides
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import javax.inject.Singleton

@Module
class DataSourcesModule {

    @Provides
    @Singleton
    fun providePreferencesDataSource() = PreferencesDataSource()

    @AssistedFactory
    interface NetworkDataSourceFactory {
        fun create(
            baseUrl: String
        ): NetworkDataSource
    }

    @AssistedFactory
    interface UserSettingsDataSourceFactory {
        fun create(
            @Assisted("shopId") shopId: String,
            @Assisted("segment") segment: String,
            @Assisted("stream") stream: String
        ): UserSettingsDataSource
    }

    @Provides
    @Singleton
    fun provideRecommendationDataSource() = RecommendationDataSource()

    @Provides
    fun provideNotificationDataSource(
        preferencesDataSource: PreferencesDataSource
    ) = NotificationDataSource(
        preferencesDataSource = preferencesDataSource
    )
}
