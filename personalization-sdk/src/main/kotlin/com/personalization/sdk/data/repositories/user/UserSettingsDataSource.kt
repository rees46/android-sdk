package com.personalization.sdk.data.repositories.user

import com.personalization.sdk.data.repositories.preferences.PreferencesDataSource
import com.personalization.sdk.domain.models.NotificationSource
import org.json.JSONObject
import javax.inject.Inject

class UserSettingsDataSource @Inject constructor(
    private val preferencesDataSource: PreferencesDataSource
) {

    private var shopId: String = ""
    private var segment: String = ""
    private var stream: String = ""
    private var userAgent: String = ""

    fun initialize(
        shopId: String,
        segment: String,
        stream: String,
        userAgent: String
    ) {
        this.shopId = shopId
        this.segment = segment
        this.stream = stream
        this.userAgent = userAgent
    }

    internal fun addParams(params: JSONObject, notificationSource: NotificationSource?): JSONObject {
        params.put(SHOP_ID_PARAMS_FIELD, shopId)

        val did = preferencesDataSource.getDid()
        if (did.isNotEmpty()) {
            params.put(DID_PARAMS_FIELD, did)
        }

        val seance = getSid()
        if (seance.isNotEmpty()) {
            params.put(SEANCE_PARAMS_FIELD, seance)
            params.put(SID_PARAMS_FIELD, seance)
        }

        params.put(SEGMENT_PARAMS_FIELD, segment)
        params.put(STREAM_PARAMS_FIELD, stream)

        if (notificationSource != null) {
            val notificationObject = JSONObject()
                .put(SOURCE_FROM_FIELD, notificationSource.type)
                .put(SOURCE_CODE_FIELD, notificationSource.id)
            params.put(SOURCE_PARAMS_FIELD, notificationObject)
        }

        return params
    }

    internal fun getSidLastActTime(): Long = preferencesDataSource.getValue(SID_LAST_ACT_KEY, DEFAULT_SID_LAST_ACT_TIME)
    internal fun saveSidLastActTime(value: Long) = preferencesDataSource.saveValue(SID_LAST_ACT_KEY, value)

    internal fun getSid(): String = preferencesDataSource.getValue(SID_KEY, DEFAULT_SID)
    internal fun saveSid(value: String) = preferencesDataSource.saveValue(SID_KEY, value)

    companion object {

        private const val DEFAULT_SID = ""
        private const val DEFAULT_SID_LAST_ACT_TIME = 0L

        private const val SID_KEY = "sid"
        private const val SID_LAST_ACT_KEY = "sid_last_act"

        private const val SHOP_ID_PARAMS_FIELD = "shop_id"
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
