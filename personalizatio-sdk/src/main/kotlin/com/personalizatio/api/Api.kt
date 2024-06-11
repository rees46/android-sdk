package com.personalizatio.api

import com.personalizatio.notifications.Source
import org.json.JSONObject

interface Api {

    fun send(apiMethod: ApiMethod, params: JSONObject, listener: OnApiCallbackListener?,
             shopId: String, did: String?, seance: String?, segment: String, stream: String, source: Source)

    companion object {

        fun getApi(baseUrl: String): Api {
            return ApiImpl(baseUrl)
        }
    }
}
