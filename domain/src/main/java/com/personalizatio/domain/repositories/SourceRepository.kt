package com.personalizatio.domain.repositories

import org.json.JSONObject

interface SourceRepository {

    fun getJsonObject(timeDuration: Int): JSONObject?

    fun update(type: String, id: String)
}
