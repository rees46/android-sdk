package com.personalization.sdk.data.di

import com.personalization.sdk.data.repositories.network.NetworkDataSource
import com.personalization.sdk.data.repositories.notification.NotificationDataSource
import com.personalization.sdk.data.repositories.preferences.PreferencesDataSource
import com.personalization.sdk.data.repositories.recommendation.RecommendationDataSource
import com.personalization.sdk.data.repositories.user.UserSettingsDataSource
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
    fun provideNetworkDataSource() = NetworkDataSource()

    @Provides
    @Singleton
    fun provideUserDataSource(
        preferencesDataSource: PreferencesDataSource
    ) = UserSettingsDataSource(
        preferencesDataSource = preferencesDataSource
    )

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
