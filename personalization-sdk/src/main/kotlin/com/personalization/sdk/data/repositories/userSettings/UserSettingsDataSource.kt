package com.personalization.sdk.data.repositories.userSettings

import com.personalization.sdk.data.repositories.preferences.PreferencesDataSource
import com.personalization.sdk.domain.models.NotificationSource
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import org.json.JSONObject

class UserSettingsDataSource @AssistedInject constructor(
    private val preferencesDataSource: PreferencesDataSource,
    @Assisted("shopId") private val shopId: String,
    @Assisted("shopSecretKey") private val shopSecretKey: String,
    @Assisted("segment") private val segment: String,
    @Assisted("stream") private val stream: String
) {

    private var isInitialized: Boolean = false

    private fun addOptionalParam(params: JSONObject, key: String, value: String?) {
    value?.let { params.put(key, it) }
}

    internal fun addParams(
        params: JSONObject,
        notificationSource: NotificationSource?,
        isSecret: Boolean = false
    ): JSONObject {
        params.put(SHOP_ID_PARAMS_FIELD, shopId)

        if (isSecret) {
            params.put(SHOP_SECRET_KEY_PARAMS_FIELD, shopSecretKey)
        }

        addOptionalParam(params, DID_PARAMS_FIELD, getDid())
        addOptionalParam(params, SID_PARAMS_FIELD, getSid())
        addOptionalParam(params, SEANCE_PARAMS_FIELD, getSid())
        params.put(SEGMENT_PARAMS_FIELD, segment)
        params.put(STREAM_PARAMS_FIELD, stream)

        notificationSource?.let {
            val notificationObject = JSONObject()
                .put(SOURCE_FROM_FIELD, it.type)
                .put(SOURCE_CODE_FIELD, it.id)
            params.put(SOURCE_PARAMS_FIELD, notificationObject)
        }

        return params
    }

    internal fun getSidLastActTime(): Long = preferencesDataSource.getValue(SID_LAST_ACT_KEY, DEFAULT_SID_LAST_ACT_TIME)
    internal fun saveSidLastActTime(value: Long) = preferencesDataSource.saveValue(SID_LAST_ACT_KEY, value)

    internal fun getSid(): String = preferencesDataSource.getValue(SID_KEY, DEFAULT_SID)
    internal fun saveSid(value: String) = preferencesDataSource.saveValue(SID_KEY, value)

    internal fun getDid(): String = preferencesDataSource.getValue(DID_KEY, DEFAULT_DID)
    internal fun saveDid(value: String) = preferencesDataSource.saveValue(DID_KEY, value)

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

        private const val SHOP_ID_PARAMS_FIELD = "shop_id"
        private const val SHOP_SECRET_KEY_PARAMS_FIELD = "shop_secret"
        private const val DID_PARAMS_FIELD = "did"
        private const val SEANCE_PARAMS_FIELD = "seance"
        private const val SID_PARAMS_FIELD = "sid"
        private const val SEGMENT_PARAMS_FIELD = "segment"
        private const val STREAM_PARAMS_FIELD = "stream"
        private const val SOURCE_PARAMS_FIELD = "source"
        private const val SOURCE_FROM_FIELD = "from"
        private const val SOURCE_CODE_FIELD = "code"
    }
}
