package com.personalization.data.di

import com.personalization.data.mappers.NotificationMapper
import dagger.Module
import dagger.Provides

@Module
class ModelsModule {

    @Provides
    fun provideNotificationMapper() = NotificationMapper()
}
