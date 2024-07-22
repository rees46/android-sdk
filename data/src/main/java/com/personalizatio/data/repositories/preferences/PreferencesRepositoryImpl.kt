package com.personalizatio.data.repositories.preferences

import android.content.SharedPreferences
import com.personalizatio.domain.repositories.PreferencesRepository
import javax.inject.Inject

class PreferencesRepositoryImpl @Inject constructor(
    private val preferencesDataSource: PreferencesDataSource
) : PreferencesRepository {

    override fun init(sharedPreferences: SharedPreferences) {
        preferencesDataSource.init(sharedPreferences)
    }

    override fun<T> getValue(field: String, defaultValue: T?): Any? {
        return preferencesDataSource.getValue(field, defaultValue)
    }

    override fun<T> saveValue(field: String, value: T) {
        preferencesDataSource.saveValue(field, value)
    }
}
