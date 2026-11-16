package com.personalization.sdk.domain.usecases.network

import com.personalization.api.OnApiCallbackListener
import com.personalization.sdk.data.repositories.network.NetworkRepositoryImpl
import com.personalization.sdk.domain.repositories.NetworkRepository
import javax.inject.Inject
import org.json.JSONObject

class SendNetworkMethodUseCase @Inject constructor(
    private val networkRepository: NetworkRepository
) {

    /**
     * Whether events go through the new core's queue, which stamps the tracking source when it sends
     * them: the callers then leave it out.
     */
    internal val queuesEvents: Boolean
        get() = (networkRepository as? NetworkRepositoryImpl)?.core?.isV2 == true

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
