package com.personalization.sdk.data.repositories.advertising

import android.content.Context
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import com.personalization.sdk.domain.repositories.AdvertisingRepository
import java.util.UUID
import javax.inject.Inject

class AdvertisingRepositoryImpl @Inject constructor(
    private val context: Context
) : AdvertisingRepository {
    override suspend fun fetchAdvertisingId(): String =
        try {
            AdvertisingIdClient.getAdvertisingIdInfo(context).id
        } catch (e: Exception) {
            generateDefaultAdvertisingId()
        }

    private fun generateDefaultAdvertisingId(): String {
        return UUID(0L, 0L).toString()
    }

}
