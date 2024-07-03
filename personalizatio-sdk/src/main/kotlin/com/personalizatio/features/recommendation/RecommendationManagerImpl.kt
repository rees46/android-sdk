package com.personalizatio.features.recommendation

import com.google.gson.Gson
import com.personalizatio.Params
import com.personalizatio.SDK
import com.personalizatio.api.OnApiCallbackListener
import com.personalizatio.api.managers.RecommendationManager
import com.personalizatio.api.entities.recommendation.RecommendationEntity
import com.personalizatio.api.entities.recommendation.ExtendedRecommendationEntity
import org.json.JSONObject

internal class RecommendationManagerImpl(private val sdk: SDK) : RecommendationManager {

    override fun getRecommendation(
        recommenderCode: String,
        onGetRecommendation: (RecommendationEntity) -> Unit,
        onError: (Int, String?) -> Unit
    ) {
        val params = getRecommendationParams()
        getRecommendation(recommenderCode, params, onGetRecommendation, onError)
    }

    override fun getRecommendation(recommenderCode: String, listener: OnApiCallbackListener) {
        val params = getRecommendationParams()
        getRecommendation(recommenderCode, params, listener)
    }

    override fun getRecommendation(
        recommenderCode: String,
        params: Params,
        onGetRecommendation: (RecommendationEntity) -> Unit,
        onError: (Int, String?) -> Unit
    ) {
        getRecommendation(recommenderCode, params, object : OnApiCallbackListener() {
            override fun onSuccess(response: JSONObject?) {
                response?.let {
                    val recommendationEntity = Gson().fromJson(it.toString(), RecommendationEntity::class.java)
                    onGetRecommendation(recommendationEntity)
                }
            }

            override fun onError(code: Int, msg: String?) {
                onError(code, msg)
            }
        })
    }

    override fun getExtendedRecommendation(
        recommenderCode: String,
        onGetExtendedRecommendation: (ExtendedRecommendationEntity) -> Unit,
        onError: (Int, String?) -> Unit
    ) {
        val params = getRecommendationParams(extended = true)
        getExtendedRecommendation(recommenderCode, params, onGetExtendedRecommendation, onError)
    }

    override fun getExtendedRecommendation(
        recommenderCode: String,
        params: Params,
        onGetExtendedRecommendation: (ExtendedRecommendationEntity) -> Unit,
        onError: (Int, String?) -> Unit
    ) {
        getExtendedRecommendation(recommenderCode, params, object : OnApiCallbackListener() {
            override fun onSuccess(response: JSONObject?) {
                response?.let {
                    val extendedRecommendationEntity = Gson().fromJson(it.toString(), ExtendedRecommendationEntity::class.java)
                    onGetExtendedRecommendation.invoke(extendedRecommendationEntity)
                }
            }

            override fun onError(code: Int, msg: String?) {
                onError(code, msg)
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
