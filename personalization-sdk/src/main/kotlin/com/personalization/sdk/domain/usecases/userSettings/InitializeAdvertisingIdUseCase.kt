package com.personalization.sdk.domain.usecases.userSettings

import android.content.Context
import com.personalization.core.internal.DeviceStorage
import com.personalization.sdk.domain.repositories.AdvertisingRepository
import com.personalization.sdk.domain.repositories.UserSettingsRepository
import javax.inject.Inject

internal class InitializeAdvertisingIdUseCase @Inject constructor(
    private val advertisingRepository: AdvertisingRepository,
    private val userSettingsRepository: UserSettingsRepository,
    private val context: Context,
) {
    /** Reads the advertising id and keeps it in the shop's partition, as before, and at the device level. */
    suspend fun invoke(): String {
        val advertisingId = advertisingRepository.fetchAdvertisingId()
        userSettingsRepository.updateAdvertisingId(advertisingId)
        DeviceStorage(context).saveAdvertisingId(advertisingId)
        return advertisingId
    }
}
