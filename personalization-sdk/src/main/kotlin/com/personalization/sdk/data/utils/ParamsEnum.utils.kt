package com.personalization.sdk.data.utils

import org.json.JSONObject

object ParamsEnumUtils {

    fun addOptionalParam(params: JSONObject, key: String, value: String?) {
        value?.let { params.put(key, it) }
    }

    fun addOptionalParam(params: JSONObject, key: String, value: Int?) {
        value?.let { params.put(key, it) }
    }
}
