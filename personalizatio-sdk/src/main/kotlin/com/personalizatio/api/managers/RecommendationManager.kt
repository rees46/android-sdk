package com.personalizatio.api.managers

import com.personalizatio.Params
import com.personalizatio.api.OnApiCallbackListener
import com.personalizatio.api.entities.recommendation.ExtendedRecommendationEntity
import com.personalizatio.api.entities.recommendation.RecommendationEntity

interface RecommendationManager {

    /**
     * Request a dynamic block of recommendations
     *
     * @param recommenderCode Recommendation block code
     * @param listener Callback
     *
     */
    fun getRecommendation(
        recommenderCode: String,
        listener: OnApiCallbackListener
    )

    /**
     * Request a dynamic block of recommendations
     *
     * @param recommenderCode Code of the dynamic block of recommendations
     * @param params Parameters for the request
     * @param listener Callback
     */
    fun getRecommendation(
        recommenderCode: String,
        params: Params,
        listener: OnApiCallbackListener
    )

    /**
     * Request a dynamic block of recommendations
     *
     * @param recommenderCode Recommendation block code
     * @param onGetRecommendation Callback for get recommendation
     * @param onError Callback for error
     */
    fun getRecommendation(
        recommenderCode: String,
        onGetRecommendation: (RecommendationEntity) -> Unit,
        onError: (Int, String?) -> Unit = { _: Int, _: String? -> }
    )

    /**
     * Request a dynamic block of recommendations
     *
     * @param recommenderCode Recommendation block code
     * @param onGetRecommendation Callback for get recommendation
     * @param onError Callback for error
     */
    fun getRecommendation(
        recommenderCode: String,
        params: Params,
        onGetRecommendation: (RecommendationEntity) -> Unit,
        onError: (Int, String?) -> Unit = { _: Int, _: String? -> }
    )

    /**
     * Request a dynamic block of recommendations with all information about recommended products
     *
     *
     * @param recommenderCode Recommendation block code
     * @param listener Callback
     *
     */
    fun getExtendedRecommendation(
        recommenderCode: String,
        listener: OnApiCallbackListener
    )

    /**
     * Request a dynamic block of recommendations with all information about recommended products
     *
     *
     * @param recommenderCode Recommendation block code
     * @param params Params
     * @param listener Callback
     *
     */
    fun getExtendedRecommendation(
        recommenderCode: String,
        params: Params,
        listener: OnApiCallbackListener
    )

    /**
     * Request a dynamic block of recommendations with all information about recommended products
     *
     *
     * @param recommenderCode Recommendation block code
     * @param onGetExtendedRecommendation Callback for get extended recommendation
     * @param onError Callback for error
     */
    fun getExtendedRecommendation(
        recommenderCode: String,
        onGetExtendedRecommendation: (ExtendedRecommendationEntity) -> Unit,
        onError: (Int, String?) -> Unit = { _: Int, _: String? -> }
    )

    /**
     * Request a dynamic block of recommendations with all information about recommended products
     *
     *
     * @param recommenderCode Recommendation block code
     * @param onGetExtendedRecommendation Callback for get extended recommendation
     * @param onError Callback for error
     */
    fun getExtendedRecommendation(
        recommenderCode: String,
        params: Params,
        onGetExtendedRecommendation: (ExtendedRecommendationEntity) -> Unit,
        onError: (Int, String?) -> Unit = { _: Int, _: String? -> }
    )
}
