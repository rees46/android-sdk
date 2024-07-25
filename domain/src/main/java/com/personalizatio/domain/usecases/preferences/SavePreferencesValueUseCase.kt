package com.personalizatio.domain.usecases.preferences

import com.personalizatio.domain.repositories.PreferencesRepository
import javax.inject.Inject

class SavePreferencesValueUseCase @Inject constructor(
    private val preferencesRepository: PreferencesRepository
) {

    fun saveSid(value: String) = preferencesRepository.saveSid(value)

    fun saveLastActTime(value: Long) = preferencesRepository.saveSidLastActTime(value)

    fun saveDid(value: String) = preferencesRepository.saveDid(value)

    fun saveToken(value: String) = preferencesRepository.saveToken(value)

    fun saveLastPushTokenDate(value: Long) = preferencesRepository.saveLastPushTokenDate(value)
}
