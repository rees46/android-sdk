package com.personalization.sdk.data.repositories.notification

import com.personalization.sdk.data.models.NotificationSourceDto
import com.personalization.sdk.data.repositories.preferences.PreferencesDataSource
import javax.inject.Inject

class NotificationDataSourceImpl @Inject constructor(
    private val preferencesDataSource: PreferencesDataSource
) : NotificationDataSource {

    override fun getNotificationSourceDto(): NotificationSourceDto {
        return NotificationSourceDto(
            type = getType(),
            id = getId(),
            time = getTime()
        )
    }

    private fun getType(): String = preferencesDataSource.getValue(SOURCE_TYPE_KEY, DEFAULT_TYPE)
    override fun saveType(value: String) = preferencesDataSource.saveValue(SOURCE_TYPE_KEY, value)

    private fun getId(): String = preferencesDataSource.getValue(SOURCE_ID_KEY, DEFAULT_ID)
    override fun saveId(value: String) = preferencesDataSource.saveValue(SOURCE_ID_KEY, value)

    private fun getTime(): Long = preferencesDataSource.getValue(SOURCE_TIME_KEY, DEFAULT_TIME)
    override fun saveTime(value: Long) = preferencesDataSource.saveValue(SOURCE_TIME_KEY, value)

    companion object {
        private const val DEFAULT_TYPE: String = ""
        private const val DEFAULT_ID: String = ""
        private const val DEFAULT_TIME: Long = 0L

        private const val SOURCE_TYPE_KEY: String = "source_type"
        private const val SOURCE_ID_KEY: String = "source_id"
        private const val SOURCE_TIME_KEY: String = "source_time"
    }
}
