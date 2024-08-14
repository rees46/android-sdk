package com.personalization.sdk.data.repositories.user

import com.personalization.sdk.domain.repositories.UserSettingsRepository
import javax.inject.Inject

class UserSettingsRepositoryImpl @Inject constructor(
    private val userSettingsDataSource: UserSettingsDataSource
) : UserSettingsRepository {

    override fun initialize(
        shopId: String,
        segment: String,
        stream: String,
        userAgent: String
    ) {
        userSettingsDataSource.initialize(
            shopId = shopId,
            segment = segment,
            stream = stream,
            userAgent = userAgent
        )
    }

    override fun getDid(): String =
        userSettingsDataSource.getDid()

    override fun updateDid(value: String) {
        userSettingsDataSource.saveDid(value)
    }

    override fun updateSid(value: String) {
        userSettingsDataSource.saveSid(value)
        updateSidLastActTime()
    }

    override fun getSid(): String =
        userSettingsDataSource.getSid()

    override fun updateSidLastActTime() {
        userSettingsDataSource.saveSidLastActTime(System.currentTimeMillis())
    }

    override fun getSidLastActTime(): Long =
        userSettingsDataSource.getSidLastActTime()
}
