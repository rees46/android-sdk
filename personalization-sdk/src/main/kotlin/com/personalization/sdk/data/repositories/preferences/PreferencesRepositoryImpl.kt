package com.personalization.sdk.data.repositories.preferences

import android.content.Context
import com.personalization.sdk.domain.repositories.PreferencesRepository
import javax.inject.Inject

class PreferencesRepositoryImpl @Inject constructor(
    private val preferencesDataSource: PreferencesDataSource
) : PreferencesRepository {

    override fun initialize(
        context: Context,
        preferencesKey: String
    ) = preferencesDataSource.initialize(
        context = context,
        preferencesKey = preferencesKey
    )

    override fun getPushToken(provider: String): String = preferencesDataSource.getPushToken(provider)
    override fun savePushToken(provider: String, value: String) {
        preferencesDataSource.savePushToken(provider, value)
    }

    override fun getLastPushTokenDate(provider: String): Long =
        preferencesDataSource.getLastPushTokenDate(provider)
    override fun saveLastPushTokenDate(provider: String, value: Long) {
        preferencesDataSource.saveLastPushTokenDate(provider, value)
    }
}
