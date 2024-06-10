package com.personalizatio.notifications

import android.content.SharedPreferences
import org.json.JSONObject

data class Source(private var type: String?, private var id: String?,  var time: Long) {

    fun getJsonObject(timeDuration: Int) : JSONObject? {
        if (type == null || !isTimeCorrect(timeDuration)) {
            return null
        }

        val jsonObject = JSONObject()
        jsonObject.put("from", type)
        jsonObject.put("code", id)
        return jsonObject
    }

    /**
     * Сохраняет данные источника
     *
     * @param type тип источника: bulk, chain, transactional
     * @param id   идентификатор сообщения
     */
    fun update(type: String, id: String, preferences: SharedPreferences) {
        this.type = type
        this.id = id
        this.time = System.currentTimeMillis()

        preferences.edit()
            .putString(SOURCE_TYPE_PREFS_KEY, type)
            .putString(SOURCE_ID_PREFS_KEY, id)
            .putLong(SOURCE_TIME_PREFS_KEY, time)
            .apply()
    }

    private fun isTimeCorrect(timeDuration: Int) : Boolean {
        return time > 0 && time + timeDuration > System.currentTimeMillis()
    }

    companion object {
        private const val SOURCE_TYPE_PREFS_KEY: String = "source_type"
        private const val SOURCE_ID_PREFS_KEY: String = "source_id"
        private const val SOURCE_TIME_PREFS_KEY: String = "source_time"

        fun createSource(preferences: SharedPreferences) : Source {
            val type = preferences.getString(SOURCE_TYPE_PREFS_KEY, null)
            val id = preferences.getString(SOURCE_ID_PREFS_KEY, null)
            val time = preferences.getLong(SOURCE_TIME_PREFS_KEY, 0)
            return Source(type, id, time)
        }
    }
}