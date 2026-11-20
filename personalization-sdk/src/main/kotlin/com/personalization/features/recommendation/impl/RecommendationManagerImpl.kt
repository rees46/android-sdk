package com.personalization.features.recommendation.impl

import com.google.gson.Gson
import com.personalization.Params
import com.personalization.api.OnApiCallbackListener
import com.personalization.api.managers.RecommendationManager
import com.personalization.api.responses.recommendation.GetExtendedRecommendationResponse
import com.personalization.api.responses.recommendation.GetRecommendationResponse
import com.personalization.core.internal.isEmptyAnswer
import com.personalization.sdk.domain.usecases.network.SendNetworkMethodUseCase
import javax.inject.Inject
import org.json.JSONObject

internal class RecommendationManagerImpl @Inject constructor(
    private val sendNetworkMethodUseCase: SendNetworkMethodUseCase
) : RecommendationManager {

    override fun getRecommendation(
        recommenderCode: String,
        params: Params,
        onGetRecommendation: (GetRecommendationResponse) -> Unit,
        onError: (Int, String?) -> Unit
    ) {
        params.put(EXTENDED_PARAMETER, false)

        getRecommendation(
            recommenderCode = recommenderCode,
            params = params,
            listener = object : OnApiCallbackListener() {
                override fun onSuccess(response: JSONObject?) {
                    // A 2xx without a body is a block without products: the callback hears it too.
                    val getRecommendationResponse = if (response.isEmptyAnswer()) {
                        GetRecommendationResponse(html = "", id = 0, productIds = emptyList(), title = "")
                    } else {
                        Gson().fromJson(response.toString(), GetRecommendationResponse::class.java)
                    }
                    onGetRecommendation(getRecommendationResponse)
                }

                override fun onError(code: Int, msg: String?) {
                    onError(code, msg)
                }
            }
        )
    }

    override fun getExtendedRecommendation(
        recommenderCode: String,
        params: Params,
        onGetExtendedRecommendation: (GetExtendedRecommendationResponse) -> Unit,
        onError: (Int, String?) -> Unit
    ) {
        params.put(EXTENDED_PARAMETER, true)

        getRecommendation(
            recommenderCode = recommenderCode,
            params = params,
            listener = object : OnApiCallbackListener() {
                override fun onSuccess(response: JSONObject?) {
                    val getExtendedRecommendationResponse = if (response.isEmptyAnswer()) {
                        GetExtendedRecommendationResponse(html = "", id = 0, products = emptyList(), title = "")
                    } else {
                        Gson().fromJson(response.toString(), GetExtendedRecommendationResponse::class.java)
                    }
                    onGetExtendedRecommendation(getExtendedRecommendationResponse)
                }

                override fun onError(code: Int, msg: String?) {
                    onError(code, msg)
                }
            }
        )
    }

    override fun getRecommendation(
        recommenderCode: String,
        params: Params,
        listener: OnApiCallbackListener
    ) {
        sendNetworkMethodUseCase.getAsync(
            "$GET_RECOMMENDATION_REQUEST/$recommenderCode",
            params.build(),
            listener
        )
    }

    companion object {
        const val GET_RECOMMENDATION_REQUEST = "recommend"

        const val EXTENDED_PARAMETER = "extended"
    }
}
