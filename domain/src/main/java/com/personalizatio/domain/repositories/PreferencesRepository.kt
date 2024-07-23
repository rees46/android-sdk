package com.personalizatio.domain.repositories

import android.content.SharedPreferences

interface PreferencesRepository {

    fun initialize(
        sharedPreferences: SharedPreferences,
        preferencesKey: String
    )

    fun getSidLastActTime(defaultValue: Long): Long
    fun saveSidLastActTime(value: Long)

    fun getSid(defaultValue: String?):  String?
    fun saveSid(value: String)

    fun getDid(defaultValue: String?): String?
    fun saveDid(value: String)

    fun getToken(defaultValue: String?): String?
    fun saveToken(value: String)

    fun getLastPushTokenDate(defaultValue: Long): Long
    fun saveLastPushTokenDate(value: Long)

    fun getSegment(defaultValue: String): String
}
