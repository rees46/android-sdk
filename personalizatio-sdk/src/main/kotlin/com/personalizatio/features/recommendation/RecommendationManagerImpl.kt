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
        imageSize: Int?,
        withLocations: Boolean?,
        onGetRecommendation: (GetRecommendationResponse) -> Unit,
        onError: (Int, String?) -> Unit
    ) {
        val params = getRecommendationParams(false, imageSize, withLocations)

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
        imageSize: Int?,
        withLocations: Boolean?,
        onGetExtendedRecommendation: (GetExtendedRecommendationResponse) -> Unit,
        onError: (Int, String?) -> Unit
    ) {
        val params = getRecommendationParams(true, imageSize, withLocations)

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

    private fun getRecommendationParams(
        extended: Boolean?,
        imageSize: Int?,
        withLocations: Boolean?) : Params {
        val params = Params()

        if(extended != null) params.put(EXTENDED_PARAMETER, extended)
        if(imageSize != null) params.put(IMAGE_SIZE_PARAMETER, imageSize)
        if(withLocations != null) params.put(WITH_LOCATIONS_PARAMETER, withLocations)

        return params
    }

    companion object {
        const val GET_RECOMMENDATION_REQUEST = "recommend"

        const val EXTENDED_PARAMETER = "extended"
        const val IMAGE_SIZE_PARAMETER = "resize_image"
        const val WITH_LOCATIONS_PARAMETER = "with_locations"
    }
}
