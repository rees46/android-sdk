package com.personalization.sdk.domain.usecases.userSettings

import com.personalization.sdk.domain.repositories.AdvertisingRepository
import com.personalization.sdk.domain.repositories.UserSettingsRepository
import javax.inject.Inject

internal class InitializeAdvertisingIdUseCase @Inject constructor(
    private val advertisingRepository: AdvertisingRepository,
    private val userSettingsRepository: UserSettingsRepository
) {
    suspend fun invoke() {
        val advertisingId = advertisingRepository.fetchAdvertisingId()
        userSettingsRepository.saveAdvertisingId(advertisingId)
    }
}