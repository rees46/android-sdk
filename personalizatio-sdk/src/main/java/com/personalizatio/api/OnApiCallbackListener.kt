package com.personalizatio.api

import org.json.JSONArray
import org.json.JSONObject

abstract class OnApiCallbackListener {
    open fun onSuccess(response: JSONObject?) {}
    fun onSuccess(response: JSONArray) {}
    open fun onError(code: Int, msg: String?) {}
}