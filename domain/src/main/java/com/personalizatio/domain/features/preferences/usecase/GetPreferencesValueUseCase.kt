package com.personalizatio.domain.features.preferences.usecase

import com.personalizatio.data.repository.preferences.PreferencesRepository
import javax.inject.Inject

class GetPreferencesValueUseCase @Inject constructor(
    private val preferencesRepository: PreferencesRepository
) {

    operator fun invoke(field: String, defaultValue: String? = null) : String? =
        preferencesRepository.getValue(field, defaultValue)

    operator fun invoke(field: String, defaultValue: Long) : Long =
        preferencesRepository.getValue(field, defaultValue)
}
