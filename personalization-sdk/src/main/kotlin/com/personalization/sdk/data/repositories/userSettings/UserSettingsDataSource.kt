package com.personalization.sdk.data.repositories.userSettings

import com.personalization.sdk.data.models.params.UserSettingsParams
import com.personalization.sdk.data.repositories.preferences.PreferencesDataSource
import com.personalization.sdk.data.utils.ParamsEnumUtils.addOptionalParam
import com.personalization.sdk.domain.models.NotificationSource
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import org.json.JSONObject

class UserSettingsDataSource @AssistedInject constructor(
    private val preferencesDataSource: PreferencesDataSource,
    @Assisted("shopId") private val shopId: String,
    @Assisted("segment") private val segment: String,
    @Assisted("stream") private val stream: String
) {

    private var isInitialized: Boolean = false

    internal fun addParams(
        params: JSONObject,
        notificationSource: NotificationSource?,
    ): JSONObject {
        params.put(UserSettingsParams.SHOP_ID, shopId)

        addOptionalParam(params, UserSettingsParams.DID, getDid())
        addOptionalParam(params, UserSettingsParams.SID, getSid())
        addOptionalParam(params, UserSettingsParams.SEANCE, getSid())
        params.put(UserSettingsParams.SEGMENT, segment)
        params.put(UserSettingsParams.STREAM, stream)

        notificationSource?.let {
            val notificationObject = JSONObject()
                .put(UserSettingsParams.SOURCE_FROM, it.type)
                .put(UserSettingsParams.SOURCE_CODE, it.id)
            params.put(UserSettingsParams.SOURCE, notificationObject)
        }

        return params
    }

    internal fun getSidLastActTime(): Long =
        preferencesDataSource.getValue(SID_LAST_ACT_KEY, DEFAULT_SID_LAST_ACT_TIME)

    internal fun saveSidLastActTime(value: Long) =
        preferencesDataSource.saveValue(SID_LAST_ACT_KEY, value)

    internal fun getSid(): String = preferencesDataSource.getValue(SID_KEY, DEFAULT_SID)
    internal fun saveSid(value: String) = preferencesDataSource.saveValue(SID_KEY, value)

    internal fun getDid(): String = preferencesDataSource.getValue(DID_KEY, DEFAULT_DID)
    internal fun saveDid(value: String) = preferencesDataSource.saveValue(DID_KEY, value)
    internal fun removeDid() = preferencesDataSource.removeValue(DID_KEY)

    internal fun getIsInitialized(): Boolean = isInitialized
    internal fun setIsInitialized(value: Boolean) {
        isInitialized = value
    }

    companion object {

        private const val DEFAULT_DID = ""
        private const val DEFAULT_SID = ""
        private const val DEFAULT_SID_LAST_ACT_TIME = 0L

        private const val DID_KEY = "did"
        private const val SID_KEY = "sid"
        private const val SID_LAST_ACT_KEY = "sid_last_act"
    }
}
