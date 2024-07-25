package com.personalizatio.domain.repositories

import android.content.Context

interface PreferencesRepository {

    fun initialize(
        context: Context,
        preferencesKey: String
    )

    fun getSidLastActTime(): Long
    fun saveSidLastActTime(value: Long)

    fun getSid():  String
    fun saveSid(value: String)

    fun getDid(): String
    fun saveDid(value: String)

    fun getToken(): String
    fun saveToken(value: String)

    fun getLastPushTokenDate(): Long
    fun saveLastPushTokenDate(value: Long)

    fun getSegment(): String
}
