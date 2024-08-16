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

        internal const val TWO_DAYS_MILLISECONDS  = 60 * 60 * 24 * 2 * 1000
    }
}
