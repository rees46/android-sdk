package com.personalization.sdk.domain.repositories

import com.personalization.api.OnApiCallbackListener
import com.personalization.sdk.domain.models.NetworkMethod
import org.json.JSONObject

interface NetworkRepository {

    fun initialize(
        baseUrl: String,
        shopId: String,
        seance: String?,
        segment: String,
        stream: String,
        userAgent: String
    )

    fun sendMethod(
        networkMethod: NetworkMethod,
        params: JSONObject,
        listener: OnApiCallbackListener?
    )
}
