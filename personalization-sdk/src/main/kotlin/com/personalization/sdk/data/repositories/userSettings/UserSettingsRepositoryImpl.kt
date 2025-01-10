package com.personalization.sdk.data.repositories.userSettings

import com.personalization.sdk.data.repositories.preferences.PreferencesDataSource
import com.personalization.sdk.domain.repositories.UserSettingsRepository
import javax.inject.Inject

private const val DEFAULT_DID = ""
private const val DEFAULT_SID = ""
private const val DEFAULT_SID_LAST_ACT_TIME = 0L
private const val DEFAULT_SHOP_ID = ""
private const val DEFAULT_STREAM = "android"

private const val DID_KEY = "did"
private const val SID_KEY = "sid"
private const val SID_LAST_ACT_KEY = "sid_last_act"
private const val SHOP_ID_KEY = "shop_id"
private const val SEGMENT_AB_TESTING_KEY = "segment_ab_testing"
private const val STREAM_KEY = "stream"

class UserSettingsRepositoryImpl @Inject constructor(
    private val preferencesDataSource: PreferencesDataSource,
) : UserSettingsRepository {

    override fun getDid(): String = preferencesDataSource.getValue(
        field = DID_KEY,
        defaultValue = DEFAULT_DID
    )

    override fun updateDid(value: String) {
        preferencesDataSource.saveValue(
            field = DID_KEY,
            value = value
        )
    }

    override fun removeDid() {
        preferencesDataSource.removeValue(field = DID_KEY)
    }

    override fun getSid(): String = preferencesDataSource.getValue(
        field = SID_KEY,
        defaultValue = DEFAULT_SID
    )

    override fun updateSid(value: String) {
        preferencesDataSource.saveValue(
            field = SID_KEY,
            value = value
        )
        updateSidLastActTime()
    }

    override fun getSidLastActTime(): Long = preferencesDataSource.getValue(
        field = SID_LAST_ACT_KEY,
        defaultValue = DEFAULT_SID_LAST_ACT_TIME
    )

    override fun updateSidLastActTime() {
        preferencesDataSource.saveValue(
            field = SID_LAST_ACT_KEY,
            value = System.currentTimeMillis()
        )
    }

    override fun getShopId(): String = preferencesDataSource.getValue(
        field = SHOP_ID_KEY,
        defaultValue = DEFAULT_SHOP_ID
    )

    override fun updateShopId(value: String) {
        preferencesDataSource.saveValue(
            field = SHOP_ID_KEY,
            value = value
        )
    }

    override fun getSegmentForABTesting(): String = preferencesDataSource.getValue(
        field = SEGMENT_AB_TESTING_KEY,
        defaultValue = generateRandomSegmentValue()
    )

    override fun updateSegmentForABTesting() {
        preferencesDataSource.saveValue(
            field = SEGMENT_AB_TESTING_KEY,
            value = generateRandomSegmentValue()
        )
    }

    override fun getStream(): String = preferencesDataSource.getValue(
        field = STREAM_KEY,
        defaultValue = DEFAULT_STREAM
    )

    override fun updateStream(value: String) {
        preferencesDataSource.saveValue(
            field = STREAM_KEY,
            value = value
        )
    }

    private fun generateRandomSegmentValue(): String = arrayOf("A", "B").random()
}
