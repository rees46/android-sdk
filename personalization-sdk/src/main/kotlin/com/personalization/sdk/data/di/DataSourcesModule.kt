package com.personalization.sdk.data.di

import com.personalization.sdk.data.repositories.notification.NotificationDataSource
import com.personalization.sdk.data.repositories.notification.NotificationDataSourceImpl
import com.personalization.sdk.data.repositories.preferences.PreferencesDataSource
import com.personalization.sdk.data.repositories.preferences.PreferencesDataSourceImpl
import com.personalization.sdk.data.repositories.recommendation.RecommendationDataSource
import com.personalization.sdk.data.repositories.recommendation.RecommendationDataSourceImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
interface DataSourcesModule {

    @Binds
    @Singleton
    fun bindPreferencesDataSource(impl: PreferencesDataSourceImpl): PreferencesDataSource

    @Binds
    @Singleton
    fun bindRecommendationDataSource(impl: RecommendationDataSourceImpl): RecommendationDataSource

    companion object {

        @Provides
        fun provideNotificationDataSource(
            preferencesDataSource: PreferencesDataSource
        ): NotificationDataSource = NotificationDataSourceImpl(
            preferencesDataSource = preferencesDataSource
        )
    }
}
