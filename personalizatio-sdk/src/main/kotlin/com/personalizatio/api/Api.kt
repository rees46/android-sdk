package com.personalizatio.api

import com.personalizatio.notifications.Source
import org.json.JSONObject

internal interface Api {

    fun send(apiMethod: ApiMethod, params: JSONObject, listener: OnApiCallbackListener?,
             did: String?, seance: String?, segment: String, stream: String, source: Source)

    fun sendSecret(apiMethod: ApiMethod, params: JSONObject, listener: OnApiCallbackListener?,
                            did: String?, seance: String?, segment: String, stream: String, source: Source)

    companion object {

        fun getApi(baseUrl: String, shopId: String, shopSecretKey: String): Api {
            return ApiImpl(baseUrl, shopId, shopSecretKey)
        }
    }
}
