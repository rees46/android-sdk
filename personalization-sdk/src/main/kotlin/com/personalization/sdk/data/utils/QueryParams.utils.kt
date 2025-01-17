package com.personalization.sdk.data.utils

import org.json.JSONObject

internal object QueryParamsUtils {

    fun <T> addOptionalParam(params: JSONObject, key: String, value: T?) {
        value?.let { params.put(key, it) }
    }

    fun <T> addMultipleParams(params: JSONObject, paramsToAdd: Map<String, T?>) {
        paramsToAdd.forEach { param ->
            addOptionalParam(params, param.key, param.value)
        }
    }

    fun <T> formNewJSONWithMultipleParams(paramsToAdd: Map<String, T?>): JSONObject {
        return JSONObject().also { addMultipleParams(it, paramsToAdd) }
    }
}
