package com.personalization.sdk.domain.repositories

interface UserSettingsRepository {

    fun initialize(
        shopId: String,
        segment: String,
        stream: String,
        userAgent: String
    )

    fun getDid(): String
    fun updateDid(value: String)

    fun updateSid(value: String)
    fun getSid(): String

    fun updateSidLastActTime()
    fun getSidLastActTime(): Long

    fun getIsInitialized(): Boolean
    fun updateIsInitialized(value: Boolean)
}
