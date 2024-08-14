package com.personalization.sdk.domain.usecases.network

import com.personalization.api.OnApiCallbackListener
import com.personalization.sdk.domain.repositories.NetworkRepository
import org.json.JSONObject
import javax.inject.Inject

class SendNetworkMethodUseCase @Inject constructor(
    private val networkRepository: NetworkRepository
) {

    /**
     * Direct query execution
     */
    fun post(
        method: String,
        params: JSONObject,
        listener: OnApiCallbackListener?
    ) {
        networkRepository.post(
            method = method,
            params = params,
            listener = listener
        )
    }

    /**
     * Asynchronous execution of a request if did is not specified and initialization has not been completed
     */
    fun postAsync(
        method: String,
        params: JSONObject,
        listener: OnApiCallbackListener? = null
    ) {
        networkRepository.postAsync(
            method = method,
            params = params,
            listener = listener
        )
    }

    /**
     * Direct query execution
     */
    fun get(
        method: String,
        params: JSONObject,
        listener: OnApiCallbackListener?
    ) {
        networkRepository.get(
            method = method,
            params = params,
            listener = listener
        )
    }

    /**
     * Asynchronous execution of a request if did is not specified and initialization has not been completed
     */
    fun getAsync(
        method: String,
        params: JSONObject,
        listener: OnApiCallbackListener?
    ) {
        networkRepository.getAsync(
            method = method,
            params = params,
            listener = listener
        )
    }
}
