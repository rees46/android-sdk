package com.personalization.sdk.data.repositories.network

import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

class NetworkDataSource @AssistedInject constructor(
    @Assisted val baseUrl: String
) {

    companion object {

        internal const val TWO_DAYS_MILLISECONDS  = 60 * 60 * 24 * 2 * 1000
    }
}
