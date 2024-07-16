package com.personalizatio.data.repository.preferences

import android.content.SharedPreferences
import javax.inject.Inject

class PreferencesRepository @Inject constructor() {

    private lateinit var sharedPreferences: SharedPreferences

    fun init(sharedPreferences: SharedPreferences) {
        this.sharedPreferences = sharedPreferences
    }

    fun saveValue(field: String, value: String) {
        sharedPreferences.edit().putString(field, value).apply()
    }

    fun saveValue(field: String, value: Long) {
        sharedPreferences.edit().putLong(field, value).apply()
    }

    fun getValue(field: String, defaultValue: String?) : String? =
        sharedPreferences.getString(field, defaultValue)

    fun getValue(field: String, defaultValue: Long = 0) : Long =
        sharedPreferences.getLong(field, defaultValue)
}
