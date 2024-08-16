package com.personalization.sdk.domain.usecases.userSettings

import com.personalization.sdk.domain.repositories.UserSettingsRepository
import javax.inject.Inject

class InitUserSettingsUseCase @Inject constructor(
    private val userSettingsRepository: UserSettingsRepository
) {

    fun invoke(
        shopId: String,
        shopSecretKey: String,
        segment: String,
        stream: String
    ) {
        userSettingsRepository.initialize(
            shopId = shopId,
            shopSecretKey = shopSecretKey,
            segment = segment,
            stream = stream
        )
    }
}
