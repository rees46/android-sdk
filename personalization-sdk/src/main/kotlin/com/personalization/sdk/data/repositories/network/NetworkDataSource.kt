package com.personalization.sdk.data.repositories.network

import javax.inject.Inject

class NetworkDataSource @Inject constructor() {

    internal var baseUrl: String = ""

    fun initialize(
        baseUrl: String
    ) {
        this.baseUrl = baseUrl
    }

    companion object {

        internal var sourceTimeDuration = 60 * 60 * 24 * 2 * 1000
    }
}
