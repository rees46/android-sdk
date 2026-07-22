package com.personalization.sdk.domain.usecases.preferences

import com.personalization.PushProvider
import com.personalization.sdk.domain.repositories.PreferencesRepository
import javax.inject.Inject

class GetPreferencesValueUseCase @Inject constructor(
    private val preferencesRepository: PreferencesRepository
) {

    fun getPushToken(provider: PushProvider): String =
        preferencesRepository.getPushToken(provider.id)

    fun getLastPushTokenDate(provider: PushProvider): Long =
        preferencesRepository.getLastPushTokenDate(provider.id)
}
