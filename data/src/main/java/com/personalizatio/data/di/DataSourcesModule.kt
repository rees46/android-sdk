package com.personalizatio.data.di

import com.personalizatio.data.repositories.notification.NotificationDataSource
import com.personalizatio.data.repositories.preferences.PreferencesDataSource
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class DataSourcesModule {

    @Provides
    @Singleton
    fun providePreferencesDataSource() = PreferencesDataSource()

    @Provides
    fun provideNotificationDataSource(
        preferencesDataSource: PreferencesDataSource
    ): NotificationDataSource {
        return NotificationDataSource(
            preferencesDataSource = preferencesDataSource
        )
    }
}
