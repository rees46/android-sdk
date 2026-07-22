package com.personalization.sdk.domain.usecases.preferences

import com.personalization.PushProvider
import com.personalization.sdk.domain.repositories.PreferencesRepository
import javax.inject.Inject

class SavePreferencesValueUseCase @Inject constructor(
    private val preferencesRepository: PreferencesRepository
) {

    fun savePushToken(provider: PushProvider, value: String) =
        preferencesRepository.savePushToken(provider.id, value)

    fun saveLastPushTokenDate(provider: PushProvider, value: Long) =
        preferencesRepository.saveLastPushTokenDate(provider.id, value)
}
