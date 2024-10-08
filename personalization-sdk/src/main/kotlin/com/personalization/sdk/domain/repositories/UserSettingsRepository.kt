package com.personalization.sdk.domain.repositories

import com.personalization.sdk.domain.models.NotificationSource
import org.json.JSONObject

interface UserSettingsRepository {

    fun initialize(
        shopId: String,
        shopSecretKey: String,
        segment: String,
        stream: String
    )

    fun getDid(): String
    fun updateDid(value: String)

    fun updateSid(value: String)
    fun getSid(): String

    fun updateSidLastActTime()
    fun getSidLastActTime(): Long

    fun getIsInitialized(): Boolean
    fun updateIsInitialized(value: Boolean)

    fun addParams(
        params: JSONObject,
        notificationSource: NotificationSource?,
        isSecret: Boolean = false
    ): JSONObject
}
