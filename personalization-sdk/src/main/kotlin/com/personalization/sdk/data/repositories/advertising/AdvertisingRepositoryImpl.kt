package com.personalization.sdk.data.repositories.advertising

import android.content.Context
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import com.personalization.sdk.domain.repositories.AdvertisingRepository
import javax.inject.Inject

private const val DEFAULT_ADVERTISING_ID = "00000000-0000-0000-0000-000000000000"

class AdvertisingRepositoryImpl @Inject constructor(
    private val context: Context
) : AdvertisingRepository {
    override suspend fun fetchAdvertisingId(): String =
        try {
            AdvertisingIdClient.getAdvertisingIdInfo(context).id
        } catch (e: Exception) {
            DEFAULT_ADVERTISING_ID
        }

}