package com.personalizatio.data.repositories.preferences

import android.content.Context
import com.personalizatio.domain.repositories.PreferencesRepository
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

    override fun getSidLastActTime(defaultValue: Long) = preferencesDataSource.getSidLastActTime(defaultValue)
    override fun saveSidLastActTime(value: Long) {
        preferencesDataSource.saveSidLastActTime(value)
    }

    override fun getSid(defaultValue: String?) = preferencesDataSource.getSid(defaultValue)
    override fun saveSid(value: String) {
        preferencesDataSource.saveSid(value)
    }

    override fun getDid(defaultValue: String?) = preferencesDataSource.getDid(defaultValue)
    override fun saveDid(value: String) {
        preferencesDataSource.saveDid(value)
    }

    override fun getToken(defaultValue: String?) = preferencesDataSource.getToken(defaultValue)
    override fun saveToken(value: String) {
        preferencesDataSource.saveToken(value)
    }

    override fun getLastPushTokenDate(defaultValue: Long) = preferencesDataSource.getLastPushTokenDate(defaultValue)
    override fun saveLastPushTokenDate(value: Long) {
        preferencesDataSource.saveLastPushTokenDate(value)
    }

    override fun getSegment(defaultValue: String) = preferencesDataSource.getSegment(defaultValue)
}
