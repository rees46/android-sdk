package com.personalizatio.data.di

import com.personalizatio.data.mappers.NotificationMapper
import dagger.Module
import dagger.Provides

@Module
class ModelsModule {

    @Provides
    fun provideNotificationMapper() = NotificationMapper()
}
