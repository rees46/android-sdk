package com.personalization.sdk.data.repositories.userSettings

import com.personalization.sdk.data.di.DataSourcesModule
import com.personalization.sdk.domain.models.NotificationSource
import com.personalization.sdk.domain.repositories.UserSettingsRepository
import org.json.JSONObject
import javax.inject.Inject

class UserSettingsRepositoryImpl @Inject constructor(
    private val userSettingsDataSourceFactory: DataSourcesModule.UserSettingsDataSourceFactory,
) : UserSettingsRepository {

    private lateinit var userSettingsDataSource: UserSettingsDataSource

    override fun initialize(
        shopId: String,
        shopSecretKey: String,
        segment: String,
        stream: String
    ) {
        userSettingsDataSource = userSettingsDataSourceFactory.create(
            shopId = shopId,
            shopSecretKey = shopSecretKey,
            segment = segment,
            stream = stream
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

    override fun getIsInitialized(): Boolean =
        userSettingsDataSource.getIsInitialized()

    override fun updateIsInitialized(value: Boolean) {
        userSettingsDataSource.setIsInitialized(value)
    }

    override fun addParams(
        params: JSONObject,
        notificationSource: NotificationSource?,
        isSecret: Boolean
    ): JSONObject =
        userSettingsDataSource.addParams(
            params = params,
            notificationSource = notificationSource,
            isSecret = isSecret
        )
}
