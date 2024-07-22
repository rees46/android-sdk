package com.personalizatio.domain.usecases.preferences

import com.personalizatio.domain.repositories.PreferencesRepository
import javax.inject.Inject

class GetPreferencesValueUseCase @Inject constructor(
    private val preferencesRepository: PreferencesRepository
) {

    operator fun<T> invoke(field: String, defaultValue: T?) : Any? =
        preferencesRepository.getValue(field, defaultValue)
}
