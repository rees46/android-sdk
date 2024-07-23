package com.personalizatio.data.repositories.notification

import com.personalizatio.domain.repositories.SourceRepository
import org.json.JSONObject
import javax.inject.Inject

class SourceRepositoryImpl @Inject constructor(
    private val sourceDataSource: SourceDataSource
) : SourceRepository {

    override fun getJsonObject(timeDuration: Int): JSONObject? {
        val type = sourceDataSource.getType()

        if (type == null || !isTimeCorrect(timeDuration)) {
            return null
        }

        val id = sourceDataSource.getId()

        val jsonObject = JSONObject()
        jsonObject.put("from", type)
        jsonObject.put("code", id)
        return jsonObject
    }

    override fun update(type: String, id: String) {
        sourceDataSource.saveType(type)
        sourceDataSource.saveId(id)
        sourceDataSource.saveTime(System.currentTimeMillis())
    }

    private fun isTimeCorrect(timeDuration: Int): Boolean {
        val time = sourceDataSource.getTime()

        return time > 0 && time + timeDuration > System.currentTimeMillis()
    }
}
