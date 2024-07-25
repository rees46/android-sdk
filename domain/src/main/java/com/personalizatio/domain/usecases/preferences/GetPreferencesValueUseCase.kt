package com.personalizatio.domain.usecases.preferences

import com.personalizatio.domain.repositories.PreferencesRepository
import javax.inject.Inject

class GetPreferencesValueUseCase @Inject constructor(
    private val preferencesRepository: PreferencesRepository
) {

    fun getSidLastActTime(): Long = preferencesRepository.getSidLastActTime()

    fun getSid(): String = preferencesRepository.getSid()

    fun getDid(): String = preferencesRepository.getDid()

    fun getToken(): String = preferencesRepository.getToken()

    fun getLastPushTokenDate(): Long = preferencesRepository.getLastPushTokenDate()

    fun getSegment(): String = preferencesRepository.getSegment()
}
