package com.personalizatio.data.repository.notification

import com.personalizatio.data.repository.preferences.PreferencesRepository
import org.json.JSONObject
import javax.inject.Inject

class SourceRepository @Inject constructor(
    private val preferencesRepository: PreferencesRepository
) {
    fun getJsonObject(timeDuration: Int): JSONObject? {
        val type = preferencesRepository.getValue(SOURCE_TYPE_PREFS_KEY, null)

        if (type == null || !isTimeCorrect(timeDuration)) {
            return null
        }

        val id = preferencesRepository.getValue(SOURCE_ID_PREFS_KEY, null)

        val jsonObject = JSONObject()
        jsonObject.put("from", type)
        jsonObject.put("code", id)
        return jsonObject
    }

    fun update(type: String, id: String) {
        preferencesRepository.saveValue(SOURCE_TYPE_PREFS_KEY, type)
        preferencesRepository.saveValue(SOURCE_ID_PREFS_KEY, id)
        preferencesRepository.saveValue(SOURCE_TIME_PREFS_KEY, System.currentTimeMillis())
    }

    private fun isTimeCorrect(timeDuration: Int): Boolean {
        val time = preferencesRepository.getValue(SOURCE_TIME_PREFS_KEY, 0)

        return time > 0 && time + timeDuration > System.currentTimeMillis()
    }

    companion object {
        private const val SOURCE_TYPE_PREFS_KEY: String = "source_type"
        private const val SOURCE_ID_PREFS_KEY: String = "source_id"
        private const val SOURCE_TIME_PREFS_KEY: String = "source_time"
    }
}
