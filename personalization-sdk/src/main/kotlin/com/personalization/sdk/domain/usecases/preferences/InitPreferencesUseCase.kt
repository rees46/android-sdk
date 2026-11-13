package com.personalization.sdk.domain.usecases.preferences

import android.content.Context
import com.personalization.sdk.domain.repositories.PreferencesRepository
import javax.inject.Inject

class InitPreferencesUseCase @Inject constructor(
    private val preferencesRepository: PreferencesRepository
) {

    /**
     * Opens the shop's partition [preferencesKey]. The parameters are those of earlier releases, so a call
     * written for them binds as it did: [legacyPreferencesKey] is not read any more, the SDK reads the
     * store of older releases into the shop's partition itself, once, before this is called.
     */
    fun invoke(
        context: Context,
        preferencesKey: String,
        legacyPreferencesKey: String? = null,
        shopId: String? = null
    ) {
        preferencesRepository.initialize(
            context = context,
            preferencesKey = preferencesKey,
            shopId = shopId
        )
    }
}
