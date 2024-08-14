package com.personalization.sdk.domain.repositories

interface UserSettingsRepository {

    fun initialize(
        shopId: String,
        seance: String?,
        segment: String,
        stream: String,
        userAgent: String
    )
}
