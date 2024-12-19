package com.personalization.sdk.data.repositories.userSettings

import com.personalization.sdk.domain.models.NotificationSource
import org.json.JSONObject

interface UserSettingsDataSource {

    fun addParams(
        params: JSONObject,
        notificationSource: NotificationSource?,
    ): JSONObject

    fun getSidLastActTime(): Long

    fun saveSidLastActTime(value: Long)

    fun getSid(): String
    fun saveSid(value: String)

    fun getDid(): String
    fun saveDid(value: String)
    fun removeDid()

    fun getIsInitialized(): Boolean
    fun setIsInitialized(value: Boolean)
}
