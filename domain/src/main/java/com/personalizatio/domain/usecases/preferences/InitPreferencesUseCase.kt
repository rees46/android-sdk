package com.personalizatio.domain.usecases.preferences

import android.content.SharedPreferences
import com.personalizatio.domain.repositories.PreferencesRepository
import javax.inject.Inject

class InitPreferencesUseCase @Inject constructor(
    private val preferencesRepository: PreferencesRepository
) {

    fun execute(
        sharedPreferences: SharedPreferences,
        preferencesKey: String
    ) {
        preferencesRepository.initialize(
            sharedPreferences = sharedPreferences,
            preferencesKey = preferencesKey
        )
    }
}
