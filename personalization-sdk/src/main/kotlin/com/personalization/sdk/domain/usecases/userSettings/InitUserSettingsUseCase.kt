package com.personalization.sdk.domain.usecases.userSettings

import com.personalization.sdk.domain.repositories.UserSettingsRepository
import javax.inject.Inject

class InitUserSettingsUseCase @Inject constructor(
    private val userSettingsRepository: UserSettingsRepository
) {

    fun invoke(
        shopId: String,
        stream: String
    ) {
        userSettingsRepository.updateShopId(value = shopId)

        userSettingsRepository.updateSegmentForABTesting()

        userSettingsRepository.updateStream(value = stream)
    }
}
