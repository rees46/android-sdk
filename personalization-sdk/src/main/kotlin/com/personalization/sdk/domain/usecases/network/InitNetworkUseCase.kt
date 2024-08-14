package com.personalization.sdk.domain.usecases.network

import com.personalization.sdk.domain.repositories.NetworkRepository
import javax.inject.Inject

class InitNetworkUseCase @Inject constructor(
    private val networkRepository: NetworkRepository
) {

    fun invoke(
        baseUrl: String,
        shopId: String,
        seance: String?,
        segment: String,
        stream: String,
        userAgent: String
    ) {
        networkRepository.initialize(
            baseUrl = baseUrl,
            shopId = shopId,
            seance = seance,
            segment = segment,
            stream = stream,
            userAgent = userAgent
        )
    }
}
