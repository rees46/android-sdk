package com.personalization.sdk.domain.usecases.preferences

import android.content.Context
import com.personalization.sdk.domain.repositories.PreferencesRepository
import javax.inject.Inject

class InitPreferencesUseCase @Inject constructor(
    private val preferencesRepository: PreferencesRepository
) {

    fun invoke(
        context: Context,
        preferencesKey: String,
        shopId: String? = null
    ) {
        preferencesRepository.initialize(
            context = context,
            preferencesKey = preferencesKey,
            shopId = shopId
        )
    }

    /**
     * The form of earlier releases. [legacyPreferencesKey] is not read any more: the SDK reads the store
     * of older releases into the shop's partition itself, once, before this is called.
     */
    @Deprecated(
        message = "legacyPreferencesKey is ignored: the SDK migrates the store of older releases itself.",
        replaceWith = ReplaceWith("invoke(context, preferencesKey, shopId)")
    )
    fun invoke(
        context: Context,
        preferencesKey: String,
        legacyPreferencesKey: String?,
        shopId: String? = null
    ) = invoke(context = context, preferencesKey = preferencesKey, shopId = shopId)
}
