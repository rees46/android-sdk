package com.personalization.sdk.data.di

import com.personalization.sdk.data.mappers.notification.NotificationMapper
import dagger.Module
import dagger.Provides

@Module
class ModelsModule {

    @Provides
    fun provideNotificationMapper() = NotificationMapper()
}
