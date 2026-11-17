package com.personalization.api.managers

import com.personalization.api.models.recommendations.RecommendationRequest
import com.personalization.api.models.recommendations.RecommendationResponse
import com.personalization.core.BridgeApi
import com.personalization.core.CompletionListener
import com.personalization.core.SdkError
import com.personalization.core.SdkException

/**
 * Recommendation blocks the shop sets up in its account — the `recommendations` namespace:
 * `sdk.recommendations.get("block-code")`. The SDK adds `did`, the A/B group (`segment`) and the other
 * common fields itself. For a view, a cart or a purchase to count for the block, the app passes the source
 * `TrackingSource(TrackingSourceType.DYNAMIC, code)` to `sdk.tracking`.
 *
 * For the Flutter and React Native bridges: it runs on the new core only (`Rees46.initializeBridge`). On
 * an instance of the legacy core every call fails with [SdkError.NotInitialized] without a request.
 */
@BridgeApi
interface RecommendationsApi {

    /**
     * The products of the block [code] (`GET /recommend/{code}`) for where the customer is ([request]).
     *
     * @throws SdkException with [SdkError.NotInitialized] (the legacy core, an instance not initialized or
     * released), [SdkError.ShopBlocked], [SdkError.InvalidArgument] (an empty [code], a limit below 1,
     * `withLocations` without `extended`; before any request), [SdkError.Network], [SdkError.Timeout],
     * [SdkError.Http] (`404` for a block the shop does not have, `402` for a service not paid for),
     * [SdkError.Decoding] or [SdkError.Cancelled] (`release()`).
     */
    suspend fun get(code: String, request: RecommendationRequest = RecommendationRequest()): RecommendationResponse

    /** [get] for Java and for code outside coroutines: [listener] gets its result once, on the main thread. */
    fun get(code: String, request: RecommendationRequest, listener: CompletionListener<RecommendationResponse>)
}
