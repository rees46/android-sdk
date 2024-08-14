package com.personalization.sdk.data.repositories.user

import com.personalization.sdk.domain.repositories.UserSettingsRepository
import javax.inject.Inject

class UserSettingsRepositoryImpl @Inject constructor(
    private val userSettingsDataSource: UserSettingsDataSource
) : UserSettingsRepository {

    override fun initialize(
        shopId: String,
        seance: String?,
        segment: String,
        stream: String,
        userAgent: String
    ) {
        userSettingsDataSource.initialize(
            shopId = shopId,
            seance = seance,
            segment = segment,
            stream = stream,
            userAgent = userAgent
        )
    }
}
