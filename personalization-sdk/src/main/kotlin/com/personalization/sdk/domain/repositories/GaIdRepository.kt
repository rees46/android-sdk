package com.personalization.sdk.domain.repositories

import android.content.Context

interface GaIdRepository {
    suspend fun fetchAdId(context: Context): String
}