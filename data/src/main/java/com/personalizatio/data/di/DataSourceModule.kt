package com.personalizatio.data.di

import com.personalizatio.data.mappers.NotificationMapper
import com.personalizatio.data.repositories.notification.NotificationDataSource
import com.personalizatio.data.repositories.preferences.PreferencesDataSource
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class DataSourceModule {

    @Provides
    @Singleton
    fun providePreferencesDataSource() = PreferencesDataSource()

    @Provides
    @Singleton
    fun provideSourceDataSource(
        preferencesDataSource: PreferencesDataSource
    ): NotificationDataSource {
        return NotificationDataSource(
            preferencesDataSource = preferencesDataSource
        )
    }

    @Provides
    @Singleton
    fun provideNotificationMapper() = NotificationMapper()
}
