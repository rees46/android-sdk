package com.personalizatio.notifications

import com.personalizatio.domain.features.preferences.usecase.GetPreferencesValueUseCase
import com.personalizatio.domain.features.preferences.usecase.SavePreferencesValueUseCase
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
    fun update(type: String, id: String, savePreferencesValueUseCase: SavePreferencesValueUseCase) {
        this.type = type
        this.id = id
        this.time = System.currentTimeMillis()

        savePreferencesValueUseCase.invoke(SOURCE_TYPE_PREFS_KEY, type)
        savePreferencesValueUseCase.invoke(SOURCE_ID_PREFS_KEY, id)
        savePreferencesValueUseCase.invoke(SOURCE_TIME_PREFS_KEY, time)
    }

    private fun isTimeCorrect(timeDuration: Int) : Boolean {
        return time > 0 && time + timeDuration > System.currentTimeMillis()
    }

    companion object {
        private const val SOURCE_TYPE_PREFS_KEY: String = "source_type"
        private const val SOURCE_ID_PREFS_KEY: String = "source_id"
        private const val SOURCE_TIME_PREFS_KEY: String = "source_time"

        fun createSource(getPreferencesValueUseCase: GetPreferencesValueUseCase) : Source {
            val type = getPreferencesValueUseCase.invoke(SOURCE_TYPE_PREFS_KEY, null)
            val id = getPreferencesValueUseCase.invoke(SOURCE_ID_PREFS_KEY, null)
            val time = getPreferencesValueUseCase.invoke(SOURCE_TIME_PREFS_KEY, 0)
            return Source(type, id, time)
        }
    }
}
