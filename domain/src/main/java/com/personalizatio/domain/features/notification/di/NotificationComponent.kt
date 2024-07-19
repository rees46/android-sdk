package com.personalizatio.domain.features.notification.di

import dagger.Subcomponent

@Subcomponent
interface NotificationComponent {

    @Subcomponent.Factory
    interface Factory {
        fun create(): NotificationComponent
    }
}
