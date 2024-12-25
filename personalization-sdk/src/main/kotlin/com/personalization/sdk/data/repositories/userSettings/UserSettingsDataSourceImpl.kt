package com.personalization.sdk.data.repositories.userSettings

import com.personalization.sdk.data.models.params.UserSettingsParams
import com.personalization.sdk.data.repositories.preferences.PreferencesDataSource
import com.personalization.sdk.data.utils.QueryParamsUtils.addMultipleParams
import com.personalization.sdk.domain.models.NotificationSource
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import org.json.JSONObject

private const val DEFAULT_DID = ""
private const val DEFAULT_SID = ""
private const val DEFAULT_SID_LAST_ACT_TIME = 0L

private const val DID_KEY = "did"
private const val SID_KEY = "sid"
private const val SID_LAST_ACT_KEY = "sid_last_act"

class UserSettingsDataSourceImpl @AssistedInject constructor(
    private val preferencesDataSource: PreferencesDataSource,
    @Assisted("shopId") private val shopId: String,
    @Assisted("segment") private val segment: String,
    @Assisted("stream") private val stream: String
) : UserSettingsDataSource {

    private var isInitialized: Boolean = false

    override fun addParams(
        params: JSONObject,
        notificationSource: NotificationSource?,
    ): JSONObject {
        addMultipleParams(
            params = params,
            paramsToAdd = mapOf(
                UserSettingsParams.SHOP_ID to shopId,
                UserSettingsParams.DID to getDid(),
                UserSettingsParams.SID to getSid(),
                UserSettingsParams.SEANCE to getSid(),
                UserSettingsParams.SEGMENT to segment,
                UserSettingsParams.STREAM to stream,
                UserSettingsParams.SOURCE_FROM to notificationSource?.type,
                UserSettingsParams.SOURCE_CODE to notificationSource?.id
            )
        )
        return params
    }

    override fun getSidLastActTime(): Long =
        preferencesDataSource.getValue(SID_LAST_ACT_KEY, DEFAULT_SID_LAST_ACT_TIME)

    override fun saveSidLastActTime(value: Long) =
        preferencesDataSource.saveValue(SID_LAST_ACT_KEY, value)

    override fun getSid(): String = preferencesDataSource.getValue(SID_KEY, DEFAULT_SID)
    override fun saveSid(value: String) = preferencesDataSource.saveValue(SID_KEY, value)

    override fun getDid(): String = preferencesDataSource.getValue(DID_KEY, DEFAULT_DID)
    override fun saveDid(value: String) = preferencesDataSource.saveValue(DID_KEY, value)
    override fun removeDid() = preferencesDataSource.removeValue(DID_KEY)

    override fun getIsInitialized(): Boolean = isInitialized
    override fun setIsInitialized(value: Boolean) {
        isInitialized = value
    }
}
