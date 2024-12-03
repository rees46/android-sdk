package com.personalization.sdk.domain.usecases.userSettings

import com.personalization.sdk.domain.repositories.UserSettingsRepository
import javax.inject.Inject

class GetUserSettingsValueUseCase @Inject constructor(
    private val userSettingsRepository: UserSettingsRepository
) {

    fun getDid(): String = userSettingsRepository.getDid()

    fun removeDid() = userSettingsRepository.removeDid()

    fun getSid(): String = userSettingsRepository.getSid()

    fun getSidLastActTime(): Long = userSettingsRepository.getSidLastActTime()

    fun getIsInitialized(): Boolean = userSettingsRepository.getIsInitialized()
}
