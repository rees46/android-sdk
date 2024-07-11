package com.personalizatio.api.managers

import com.personalizatio.Params
import com.personalizatio.api.OnApiCallbackListener
import com.personalizatio.api.responses.recommendation.GetExtendedRecommendationResponse
import com.personalizatio.api.responses.recommendation.GetRecommendationResponse

interface RecommendationManager {

    /**
     * Request a dynamic block of recommendations
     *
     * @param recommenderCode Recommendation block code
     * @param params Parameters for the request
     * @param onGetRecommendation Callback for get recommendation
     * @param onError Callback for error
     */
    fun getRecommendation(
        recommenderCode: String,
        params: Params = Params(),
        onGetRecommendation: (GetRecommendationResponse) -> Unit,
        onError: (Int, String?) -> Unit = { _: Int, _: String? -> }
    )

    /**
     * Request a dynamic block of recommendations with all information about recommended products
     *
     * @param recommenderCode Recommendation block code
     * @param params Parameters for the request
     * @param onGetExtendedRecommendation Callback for get extended recommendation
     * @param onError Callback for error
     */
    fun getExtendedRecommendation(
        recommenderCode: String,
        params: Params = Params(),
        onGetExtendedRecommendation: (GetExtendedRecommendationResponse) -> Unit,
        onError: (Int, String?) -> Unit = { _: Int, _: String? -> }
    )

    /**
     * Request a dynamic block of recommendations
     *
     * @param recommenderCode Code of the dynamic block of recommendations
     * @param params Parameters for the request
     * @param listener Callback
     */
    @Deprecated(
        "This method will be removed in future versions.",
        level = DeprecationLevel.WARNING,
        replaceWith = ReplaceWith("getRecommendation(...) or getExtendedRecommendation(...)")
    )
    fun getRecommendation(
        recommenderCode: String,
        params: Params,
        listener: OnApiCallbackListener
    )
}
