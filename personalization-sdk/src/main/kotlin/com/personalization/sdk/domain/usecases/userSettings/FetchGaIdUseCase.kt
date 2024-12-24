package com.personalization.sdk.domain.usecases.userSettings

import android.content.Context
import com.personalization.sdk.domain.repositories.GaIdRepository
import com.personalization.sdk.domain.repositories.UserSettingsRepository
import javax.inject.Inject

class FetchGaIdUseCase @Inject constructor(
    private val gaIdRepository: GaIdRepository,
    private val userSettingsRepository: UserSettingsRepository
) {
    suspend fun invoke(context: Context) {
        val gaId = gaIdRepository.fetchAdId(context)
        userSettingsRepository.saveGaId(gaId)
    }
}