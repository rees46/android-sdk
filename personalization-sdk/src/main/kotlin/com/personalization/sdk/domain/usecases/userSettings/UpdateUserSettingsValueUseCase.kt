package com.personalization.sdk.domain.usecases.userSettings

import com.personalization.sdk.domain.repositories.UserSettingsRepository
import javax.inject.Inject

class UpdateUserSettingsValueUseCase @Inject constructor(
    private val userSettingsRepository: UserSettingsRepository
) {

    fun updateSid(value: String) = userSettingsRepository.updateSid(value)

    fun updateSidLastActTime() = userSettingsRepository.updateSidLastActTime()
}
