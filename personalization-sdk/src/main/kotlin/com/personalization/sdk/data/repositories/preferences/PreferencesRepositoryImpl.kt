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

    override fun getToken(): String = preferencesDataSource.getToken()
    override fun saveToken(value: String) {
        preferencesDataSource.saveToken(value)
    }

    override fun getLastPushTokenDate(): Long = preferencesDataSource.getLastPushTokenDate()
    override fun saveLastPushTokenDate(value: Long) {
        preferencesDataSource.saveLastPushTokenDate(value)
    }
}
