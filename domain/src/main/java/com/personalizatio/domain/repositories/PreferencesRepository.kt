package com.personalizatio.domain.repositories

import android.content.SharedPreferences

interface PreferencesRepository {

    fun init(sharedPreferences: SharedPreferences)

    fun saveValue(field: String, value: String)

    fun saveValue(field: String, value: Long)

    fun getValue(field: String, defaultValue: String?) : String?

    fun getValue(field: String, defaultValue: Long = 0) : Long
}
