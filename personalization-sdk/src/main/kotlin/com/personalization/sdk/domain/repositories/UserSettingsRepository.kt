package com.personalization.sdk.domain.repositories

interface UserSettingsRepository {

    fun getDid(): String
    fun removeDid()
    fun updateDid(value: String)

    fun updateSid(value: String)
    fun getSid(): String

    fun updateSidLastActTime()
    fun getSidLastActTime(): Long

    fun getShopId(): String
    fun updateShopId(value: String)

    fun getSegmentForABTesting(): String
    fun updateSegmentForABTesting()

    fun getStream(): String
    fun updateStream(value: String)
}
