package com.personalizatio.domain.repositories

import android.content.SharedPreferences

interface PreferencesRepository {

    fun init(sharedPreferences: SharedPreferences)

    fun<T> getValue(field: String, defaultValue: T?): Any?

    fun<T> saveValue(field: String, value: T)
}
