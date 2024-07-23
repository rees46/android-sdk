package com.personalizatio.data.repositories.notification

import com.personalizatio.data.repositories.preferences.PreferencesDataSource
import javax.inject.Inject

class SourceDataSource @Inject constructor(
    private val preferencesDataSource: PreferencesDataSource
) {

    internal fun getType() = preferencesDataSource.getValue(SOURCE_TYPE_KEY, null)
    internal fun saveType(value: String) = preferencesDataSource.saveValue(SOURCE_TYPE_KEY, value)

    internal fun getId() = preferencesDataSource.getValue(SOURCE_ID_KEY, null)
    internal fun saveId(value: String) = preferencesDataSource.saveValue(SOURCE_ID_KEY, value)

    internal fun getTime() = preferencesDataSource.getValue(SOURCE_TIME_KEY, 0)
    internal fun saveTime(value: Long) = preferencesDataSource.saveValue(SOURCE_TIME_KEY, value)

    companion object {
        private const val SOURCE_TYPE_KEY: String = "source_type"
        private const val SOURCE_ID_KEY: String = "source_id"
        private const val SOURCE_TIME_KEY: String = "source_time"
    }
}
