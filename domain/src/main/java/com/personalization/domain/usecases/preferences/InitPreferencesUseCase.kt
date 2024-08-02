package com.personalization.domain.usecases.preferences

import android.content.Context
import com.personalization.domain.repositories.PreferencesRepository
import javax.inject.Inject

class InitPreferencesUseCase @Inject constructor(
    private val preferencesRepository: PreferencesRepository
) {

    fun invoke(
        context: Context,
        preferencesKey: String
    ) {
        preferencesRepository.initialize(
            context = context,
            preferencesKey = preferencesKey
        )
    }
}
