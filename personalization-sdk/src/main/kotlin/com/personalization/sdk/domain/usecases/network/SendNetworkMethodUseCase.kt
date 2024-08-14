package com.personalization.sdk.domain.usecases.network

import com.personalization.api.OnApiCallbackListener
import com.personalization.sdk.domain.models.NetworkMethod
import com.personalization.sdk.domain.repositories.NetworkRepository
import org.json.JSONObject
import javax.inject.Inject

class SendNetworkMethodUseCase @Inject constructor(
    private val networkRepository: NetworkRepository
) {

    fun invoke(
        networkMethod: NetworkMethod,
        params: JSONObject,
        listener: OnApiCallbackListener?
    ) {
        networkRepository.sendMethod(
            networkMethod = networkMethod,
            params = params,
            listener = listener
        )
    }
}
