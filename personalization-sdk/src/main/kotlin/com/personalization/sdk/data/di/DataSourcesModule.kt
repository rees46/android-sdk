package com.personalization.sdk.data.di

import com.personalization.sdk.data.repositories.notification.NotificationDataSource
import com.personalization.sdk.data.repositories.notification.NotificationDataSourceImpl
import com.personalization.sdk.data.repositories.preferences.PreferencesDataSource
import com.personalization.sdk.data.repositories.preferences.PreferencesDataSourceImpl
import com.personalization.sdk.data.repositories.recommendation.RecommendationDataSource
import com.personalization.sdk.data.repositories.userSettings.UserSettingsDataSource
import com.personalization.sdk.data.repositories.userSettings.UserSettingsDataSourceImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import javax.inject.Singleton

@Module
class DataSourcesModule {

    @Provides
    @Singleton
    fun provideRecommendationDataSource() = RecommendationDataSource()
}

@Module
interface AbstractDataSourcesModule {

    @Binds
    @Singleton
    fun bindPreferencesDataSource(impl: PreferencesDataSourceImpl): PreferencesDataSource

    companion object {

        @Provides
        fun provideNotificationDataSource(
            preferencesDataSource: PreferencesDataSource
        ): NotificationDataSource = NotificationDataSourceImpl(
            preferencesDataSource = preferencesDataSource
        )

        @Provides
        @Singleton
        fun bindUserSettingsDataSource(
            preferencesDataSource: PreferencesDataSource,
            shopId: String,
            segment: String,
            stream: String
        ): UserSettingsDataSource = UserSettingsDataSourceImpl(
            preferencesDataSource = preferencesDataSource,
            shopId = shopId,
            segment = segment,
            stream = stream
        )
    }
}

@AssistedFactory
interface UserSettingsDataSourceFactory {
    fun create(
        @Assisted("shopId") shopId: String,
        @Assisted("segment") segment: String,
        @Assisted("stream") stream: String
    ): UserSettingsDataSourceImpl
}
