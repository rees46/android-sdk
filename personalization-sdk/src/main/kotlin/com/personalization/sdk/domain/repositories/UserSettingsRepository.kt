package com.personalization.sdk.domain.repositories

import com.personalization.sdk.domain.models.NotificationSource
import org.json.JSONObject

interface UserSettingsRepository {

    fun initialize(
        shopId: String,
        segment: String,
        stream: String
    )

    fun getDid(): String
    fun removeDid()
    fun updateDid(value: String)

    fun updateSid(value: String)
    fun getSid(): String

    fun updateSidLastActTime()
    fun getSidLastActTime(): Long

    fun getIsInitialized(): Boolean
    fun updateIsInitialized(value: Boolean)

    fun saveGaId(value: String)

    fun addParams(
        params: JSONObject,
        notificationSource: NotificationSource?,
    ): JSONObject
}
