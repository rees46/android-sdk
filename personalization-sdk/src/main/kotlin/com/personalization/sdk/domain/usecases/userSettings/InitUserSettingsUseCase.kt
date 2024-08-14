package com.personalization.sdk.domain.usecases.userSettings

import com.personalization.sdk.domain.repositories.UserSettingsRepository
import javax.inject.Inject

class InitUserSettingsUseCase @Inject constructor(
    private val userSettingsRepository: UserSettingsRepository
) {

    fun invoke(
        shopId: String,
        seance: String?,
        segment: String,
        stream: String,
        userAgent: String
    ) {
        userSettingsRepository.initialize(
            shopId = shopId,
            seance = seance,
            segment = segment,
            stream = stream,
            userAgent = userAgent
        )
    }
}
