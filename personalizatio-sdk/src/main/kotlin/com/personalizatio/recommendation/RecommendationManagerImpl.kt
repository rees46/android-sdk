package com.personalizatio.recommendation

import com.google.gson.Gson
import com.personalizatio.Params
import com.personalizatio.SDK
import com.personalizatio.api.OnApiCallbackListener
import com.personalizatio.api.listeners.OnRecommendationListener
import com.personalizatio.api.managers.RecommendationManager
import com.personalizatio.api.params.GetRecommendationParameter
import com.personalizatio.entities.recommendation.RecommendationEntity
import com.personalizatio.entities.recommendation.ExtendedRecommendationEntity
import org.json.JSONObject

internal class RecommendationManagerImpl(val sdk: SDK) : RecommendationManager {

    override fun getRecommendation(recommenderCode: String, listener: OnRecommendationListener) {
        val params = getRecommendationParams()
        getRecommendation(recommenderCode, params, listener)
    }

    override fun getRecommendation(recommenderCode: String, listener: OnApiCallbackListener) {
        val params = getRecommendationParams()
        getRecommendation(recommenderCode, params, listener)
    }

    override fun getRecommendation(recommenderCode: String, params: Params, listener: OnRecommendationListener) {
        if(params.contains(GetRecommendationParameter.EXTENDED.value)) {
            getExtendedRecommendation(recommenderCode, params, listener)
        }
        else {
            getRecommendation(recommenderCode, params, object : OnApiCallbackListener() {
                override fun onSuccess(response: JSONObject?) {
                    response?.let {
                        val recommendationEntity =
                            Gson().fromJson(it.toString(), RecommendationEntity::class.java)
                        listener.onGetRecommendation(recommendationEntity)
                    }
                }
            })
        }
    }

    override fun getExtendedRecommendation(recommenderCode: String, listener: OnRecommendationListener) {
        val params = getRecommendationParams(extended = true)
        getExtendedRecommendation(recommenderCode, params, listener)
    }

    override fun getExtendedRecommendation(recommenderCode: String, params: Params, listener: OnRecommendationListener) {
        getExtendedRecommendation(recommenderCode, params, object : OnApiCallbackListener() {
            override fun onSuccess(response: JSONObject?) {
                response?.let {
                    val extendedRecommendationEntity = Gson().fromJson(it.toString(), ExtendedRecommendationEntity::class.java)
                    listener.onGetExtendedRecommendation(extendedRecommendationEntity)
                }
            }
        })
    }

    override fun getExtendedRecommendation(recommenderCode: String, listener: OnApiCallbackListener) {
        val params = getRecommendationParams(extended = true)
        getRecommendation(recommenderCode, params, listener)
    }

    override fun getExtendedRecommendation(recommenderCode: String, params: Params, listener: OnApiCallbackListener) {
        params.put(GetRecommendationParameter.EXTENDED, true)
        getRecommendation(recommenderCode, params, listener)
    }

    override fun getRecommendation(recommenderCode: String, params: Params, listener: OnApiCallbackListener) {
        sdk.getAsync("$GET_RECOMMENDATION_REQUEST/$recommenderCode", params.build(), listener)
    }

    private fun getRecommendationParams(extended: Boolean? = null) : Params {
        val params = Params()

        if(extended != null) params.put(GetRecommendationParameter.EXTENDED, extended)

        return params
    }

    companion object {
        const val GET_RECOMMENDATION_REQUEST = "recommend"
    }
}
