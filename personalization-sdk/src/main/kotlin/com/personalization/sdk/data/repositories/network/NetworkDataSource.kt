package com.personalization.sdk.data.repositories.network

import com.personalization.utils.TimeUtils
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

class NetworkDataSource @AssistedInject constructor(
    @Assisted val baseUrl: String
) {

    companion object {

        internal val sourceTimeDuration = TimeUtils.TWO_DAYS.inWholeMilliseconds
    }
}
