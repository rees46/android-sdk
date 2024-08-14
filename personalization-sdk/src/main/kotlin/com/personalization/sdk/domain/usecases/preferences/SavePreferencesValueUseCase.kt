package com.personalization.sdk.domain.usecases.preferences

import com.personalization.sdk.domain.repositories.PreferencesRepository
import javax.inject.Inject

class SavePreferencesValueUseCase @Inject constructor(
    private val preferencesRepository: PreferencesRepository
) {

    fun saveDid(value: String) = preferencesRepository.saveDid(value)

    fun saveToken(value: String) = preferencesRepository.saveToken(value)

    fun saveLastPushTokenDate(value: Long) = preferencesRepository.saveLastPushTokenDate(value)
}
