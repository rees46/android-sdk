package com.personalization.api.managers

import com.personalization.api.models.search.SearchBlankResponse
import com.personalization.api.models.search.SearchFullRequest
import com.personalization.api.models.search.SearchFullResponse
import com.personalization.api.models.search.SearchInstantResponse
import com.personalization.core.BridgeApi
import com.personalization.core.CompletionListener
import com.personalization.core.SdkError
import com.personalization.core.SdkException

/**
 * Search in the shop's catalog — the `search` namespace: `sdk.search.instant("phone")`. The SDK adds
 * `did` and the other common fields itself. The methods send no search event: when the customer searched,
 * the app calls `sdk.tracking.search`; a product opened from the results counts for the search with the
 * source `TrackingSource(TrackingSourceType.INSTANT_SEARCH or FULL_SEARCH, query)`.
 *
 * For the Flutter and React Native bridges: it runs on the new core only (`Rees46.initializeBridge`). On
 * an instance of the legacy core every call fails with [SdkError.NotInitialized] without a request.
 *
 * Every method throws [SdkException] with [SdkError.NotInitialized] (the legacy core, an instance not
 * initialized or released), [SdkError.ShopBlocked], [SdkError.InvalidArgument] (before any request),
 * [SdkError.Network], [SdkError.Timeout], [SdkError.Http], [SdkError.Decoding] or [SdkError.Cancelled]
 * (`release()`). Nothing repeats a request.
 */
@BridgeApi
interface SearchApi {

    /**
     * What to show in an empty search field (`GET /search/blank`): the customer's recent queries and
     * products, or popular ones, suggestions, popular categories, brands and links.
     */
    suspend fun blank(): SearchBlankResponse

    /**
     * Products and suggestions as the customer types [query] (`GET /search`, `instant_search`). An empty
     * [query] is `invalidArgument`. [excludedMerchants] is sent only with [locations]: the server looks
     * for merchants in those locations and finds no product without them.
     */
    suspend fun instant(
        query: String,
        locations: List<String>? = null,
        excludedMerchants: List<String>? = null,
        excludedBrands: List<String>? = null,
    ): SearchInstantResponse

    /**
     * The screen of the results of [query] (`GET /search`, `full_search`) with the filters, order and page
     * of [request]. An empty [query] is `invalidArgument`.
     */
    suspend fun full(query: String, request: SearchFullRequest = SearchFullRequest()): SearchFullResponse

    /** [blank] for Java and for code outside coroutines: [listener] gets its result once, on the main thread. */
    fun blank(listener: CompletionListener<SearchBlankResponse>)

    /** [instant] for Java and for code outside coroutines: [listener] gets its result once, on the main thread. */
    fun instant(
        query: String,
        locations: List<String>?,
        excludedMerchants: List<String>?,
        excludedBrands: List<String>?,
        listener: CompletionListener<SearchInstantResponse>,
    )

    /** [full] for Java and for code outside coroutines: [listener] gets its result once, on the main thread. */
    fun full(query: String, request: SearchFullRequest, listener: CompletionListener<SearchFullResponse>)
}
