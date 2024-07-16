package com.personalizatio.domain.features.preferences.di

import dagger.Subcomponent

@Subcomponent
interface PreferencesComponent {

    @Subcomponent.Factory
    interface Factory {
        fun create(): PreferencesComponent
    }
}
