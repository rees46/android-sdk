package com.personalizatio.features.recommendation

import com.google.gson.Gson
import com.personalizatio.Params
import com.personalizatio.SDK
import com.personalizatio.api.OnApiCallbackListener
import com.personalizatio.api.managers.RecommendationManager
import com.personalizatio.api.responses.recommendation.GetRecommendationResponse
import com.personalizatio.api.responses.recommendation.GetExtendedRecommendationResponse
import org.json.JSONObject

internal class RecommendationManagerImpl(private val sdk: SDK) : RecommendationManager {

    override fun getRecommendation(
        recommenderCode: String,
        params: Params,
        onGetRecommendation: (GetRecommendationResponse) -> Unit,
        onError: (Int, String?) -> Unit
    ) {
        params.put(EXTENDED_PARAMETER, false)

        getRecommendation(recommenderCode, params, object : OnApiCallbackListener() {
            override fun onSuccess(response: JSONObject?) {
                response?.let {
                    val getRecommendationResponse = Gson().fromJson(it.toString(), GetRecommendationResponse::class.java)
                    onGetRecommendation(getRecommendationResponse)
                }
            }

            override fun onError(code: Int, msg: String?) {
                onError(code, msg)
            }
        })
    }

    override fun getExtendedRecommendation(
        recommenderCode: String,
        params: Params,
        onGetExtendedRecommendation: (GetExtendedRecommendationResponse) -> Unit,
        onError: (Int, String?) -> Unit
    ) {
        params.put(EXTENDED_PARAMETER, true)

        getRecommendation(recommenderCode, params, object : OnApiCallbackListener() {
            override fun onSuccess(response: JSONObject?) {
                response?.let {
                    val getExtendedRecommendationResponse = Gson().fromJson(it.toString(), GetExtendedRecommendationResponse::class.java)
                    onGetExtendedRecommendation.invoke(getExtendedRecommendationResponse)
                }
            }

            override fun onError(code: Int, msg: String?) {
                onError(code, msg)
            }
        })
    }

    override fun getRecommendation(recommenderCode: String, params: Params, listener: OnApiCallbackListener) {
        sdk.getAsync("$GET_RECOMMENDATION_REQUEST/$recommenderCode", params.build(), listener)
    }

    companion object {
        const val GET_RECOMMENDATION_REQUEST = "recommend"

        const val EXTENDED_PARAMETER = "extended"
    }
}
