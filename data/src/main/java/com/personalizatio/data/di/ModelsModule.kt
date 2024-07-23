package com.personalizatio.data.di

import com.personalizatio.data.mappers.NotificationMapper
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class ModelsModule {

    @Provides
    @Singleton
    fun provideNotificationMapper() = NotificationMapper()
}
