package com.personalizatio.domain.features.preferences.usecase

import com.personalizatio.domain.repositories.PreferencesRepository
import javax.inject.Inject

class SavePreferencesValueUseCase @Inject constructor(
    private val preferencesRepository: PreferencesRepository
) {

    operator fun invoke(field: String, defaultValue: String) {
        preferencesRepository.saveValue(field, defaultValue)
    }

    operator fun invoke(field: String, defaultValue: Long) {
        preferencesRepository.saveValue(field, defaultValue)
    }
}
