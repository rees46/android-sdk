package com.personalizatio.data.di

import com.personalizatio.data.repositories.preferences.PreferencesDataSource
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class DataSourceModule {

    @Provides
    @Singleton
    fun providePreferencesDataSource(): PreferencesDataSource {
        return PreferencesDataSource()
    }
}
