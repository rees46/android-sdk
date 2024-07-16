package com.personalizatio.data.di

import com.personalizatio.data.repository.preferences.PreferencesRepository
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class DataModule {

    @Singleton
    @Provides
    fun providePreferencesRepository(): PreferencesRepository {
        return PreferencesRepository()
    }
}