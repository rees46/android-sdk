package com.personalizatio.recommended

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.personalizatio.Params
import com.personalizatio.SDK
import com.personalizatio.api.OnApiCallbackListener
import com.personalizatio.entities.recommended.RecommendedEntity
import com.personalizatio.entities.recommended.RecommendedFullEntity
import org.json.JSONObject

internal class RecommendedManager(val sdk: SDK) {

    internal fun recommend(recommenderCode: String, listener: OnApiCallbackListener) {
        recommend(recommenderCode, Params(), listener)
    }

    internal fun recommend(recommenderCode: String, params: Params, listener: OnApiCallbackListener) {
        sdk.getAsync("recommend/$recommenderCode", params.build(), listener)
    }

    internal fun recommend(recommenderCode: String, listener: OnRecommendedListener) {
        recommend(recommenderCode, Params(), listener)
    }

    internal fun recommend(recommenderCode: String, params: Params, listener: OnRecommendedListener) {
        recommend(recommenderCode, params, object : OnApiCallbackListener() {
            override fun onSuccess(response: JSONObject?) {
                response?.let {
                    try {
                        val recommendedEntity = Gson().fromJson(it.toString(), RecommendedEntity::class.java)
                        listener.onGetRecommended(recommendedEntity)
                    }
                    catch (e: JsonSyntaxException) {
                        val recommendedFullEntity = Gson().fromJson(it.toString(), RecommendedFullEntity::class.java)
                        listener.onGetRecommended(recommendedFullEntity)
                    }
                }
            }
        })
    }
}