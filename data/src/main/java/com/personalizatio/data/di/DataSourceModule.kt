package com.personalizatio.data.di

import com.personalizatio.data.repositories.notification.SourceDataSource
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
    ): SourceDataSource {
        return SourceDataSource(
            preferencesDataSource = preferencesDataSource
        )
    }
}
