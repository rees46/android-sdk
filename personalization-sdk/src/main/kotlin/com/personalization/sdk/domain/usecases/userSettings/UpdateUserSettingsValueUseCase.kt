package com.personalization.sdk.domain.usecases.userSettings

import com.personalization.sdk.domain.repositories.UserSettingsRepository
import javax.inject.Inject

class UpdateUserSettingsValueUseCase @Inject constructor(
    private val userSettingsRepository: UserSettingsRepository
) {

    fun updateDid(value: String) = userSettingsRepository.updateDid(value)

    fun updateSid(value: String) = userSettingsRepository.updateSid(value)

    fun updateSidLastActTime() = userSettingsRepository.updateSidLastActTime()

    fun updateIsInitialized(value: Boolean) = userSettingsRepository.updateIsInitialized(value)
}
