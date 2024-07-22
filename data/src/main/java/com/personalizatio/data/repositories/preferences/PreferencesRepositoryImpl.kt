package com.personalizatio.data.repositories.preferences

import android.content.SharedPreferences
import com.personalizatio.domain.repositories.PreferencesRepository
import javax.inject.Inject

class PreferencesRepositoryImpl @Inject constructor()
    : PreferencesRepository {

    private lateinit var sharedPreferences: SharedPreferences

    override fun init(sharedPreferences: SharedPreferences) {
        this.sharedPreferences = sharedPreferences
    }

    override fun saveValue(field: String, value: String) {
        sharedPreferences.edit().putString(field, value).apply()
    }

    override fun saveValue(field: String, value: Long) {
        sharedPreferences.edit().putLong(field, value).apply()
    }

    override fun getValue(field: String, defaultValue: String?) : String? =
        sharedPreferences.getString(field, defaultValue)

    override fun getValue(field: String, defaultValue: Long) : Long =
        sharedPreferences.getLong(field, defaultValue)
}
