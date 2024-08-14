package com.personalization.sdk.data.repositories.network

import com.personalization.sdk.data.repositories.preferences.PreferencesDataSource
import com.personalization.sdk.domain.models.NotificationSource
import org.json.JSONObject
import javax.inject.Inject

class NetworkDataSource @Inject constructor(
    private val preferencesDataSource: PreferencesDataSource
) {

    internal var baseUrl: String = ""
    private var shopId: String = ""
    private var seance: String? = ""
    private var segment: String = ""
    private var stream: String = ""
    internal var userAgent: String = ""

    fun initialize(
        baseUrl: String,
        shopId: String,
        seance: String?,
        segment: String,
        stream: String,
        userAgent: String
    ) {
        this.baseUrl = baseUrl
        this.shopId = shopId
        this.seance = seance
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
        if (seance != null) {
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

    companion object {
        internal var sourceTimeDuration = 60 * 60 * 24 * 2 * 1000

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
