package com.personalization.sdk.data.repositories.advertising

import android.content.Context
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import com.personalization.sdk.data.utils.AdvertisingUtils
import com.personalization.sdk.domain.repositories.AdvertisingRepository
import javax.inject.Inject

class AdvertisingRepositoryImpl @Inject constructor(
    private val context: Context
) : AdvertisingRepository {
    override suspend fun fetchAdvertisingId(): String =
        try {
            AdvertisingIdClient.getAdvertisingIdInfo(context).id
        } catch (e: Exception) {
            AdvertisingUtils.DEFAULT_ADVERTISING_ID
        }
}
