package com.personalization.sdk.domain.repositories

interface AdvertisingRepository {
    suspend fun fetchAdvertisingId(): String
}
