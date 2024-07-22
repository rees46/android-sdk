package com.personalizatio.domain.usecases.preferences

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
