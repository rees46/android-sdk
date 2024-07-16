package com.personalizatio.di

import dagger.Component
import javax.inject.Singleton

@Singleton
@Component
interface SdkComponent {

    @Component.Factory
    interface Factory {
        fun create(): SdkComponent
    }
}
