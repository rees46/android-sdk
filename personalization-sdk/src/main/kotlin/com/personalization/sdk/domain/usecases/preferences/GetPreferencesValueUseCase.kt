package com.personalization.sdk.domain.usecases.preferences

import com.personalization.sdk.domain.repositories.PreferencesRepository
import javax.inject.Inject

class GetPreferencesValueUseCase @Inject constructor(
    private val preferencesRepository: PreferencesRepository
) {

    fun getToken(): String = preferencesRepository.getToken()

    fun getLastPushTokenDate(): Long = preferencesRepository.getLastPushTokenDate()

    fun getSegment(): String = preferencesRepository.getSegment()
}
