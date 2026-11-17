package com.personalization.api.managers

import com.personalization.api.models.search.AgentDialogResponse
import com.personalization.api.models.search.AgentResponse
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
 * The search agent (AI search) is a dialog the shop may show instead of the empty search: [agentEnabled]
 * says whether to, [agentDialog] opens the dialog and [agent] sends the customer's message. It lives on the
 * host of API v3, `Rees46Config.appDomain`. A product opened from the agent's answer counts for it with the
 * source `TrackingSource(agent_search, sessionId)`, which `TrackingSourceType` gets with the next major
 * release; its link already carries that attribution.
 *
 * For the Flutter and React Native bridges: it runs on the new core only (`Rees46.initializeBridge`). On
 * an instance of the legacy core every call fails with [SdkError.NotInitialized] without a request, and
 * [agentEnabled] is false.
 *
 * Every method throws [SdkException] with [SdkError.NotInitialized] (the legacy core, an instance not
 * initialized or released), [SdkError.ShopBlocked], [SdkError.InvalidArgument] (before any request),
 * [SdkError.Network], [SdkError.Timeout], [SdkError.Http], [SdkError.Decoding] or [SdkError.Cancelled]
 * (`release()`). Nothing repeats a request.
 */
@BridgeApi
interface SearchApi {

    /**
     * Whether the shop shows the search agent instead of the empty search: true when the last `/init`
     * answer's `search.blank_mode` is `agent`; false for any other value, without the field, and before
     * any answer. Read at once, without a request.
     */
    val agentEnabled: Boolean

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

    /**
     * The customer's dialog with the search agent (`GET /api/v3/storefront/search/agent`): the last 20
     * sessions and the messages of [sessionId], or of the latest session without one. [locations] — the
     * customer's locations, sent joined with commas; the profile's location without them. Call it when the
     * agent's window opens. The products of the messages come with their current price and stock; a product
     * no longer available is left out.
     *
     * Besides the errors of every method: `http(404)` for a session not found or of another customer,
     * `http(429)` over the limit (60 requests a minute from a device), `http(503)` when the agent is
     * unavailable — show [blank] instead; `http(402)` when the shop has no search service.
     */
    suspend fun agentDialog(sessionId: Int? = null, locations: List<String>? = null): AgentDialogResponse

    /**
     * Sends the customer's [message] to the search agent and returns its answer
     * (`POST /api/v3/storefront/search/agent`), in the session [sessionId] or a new one without it;
     * [locations] as in [agentDialog]. The agent may take up to 40 seconds and two more attempts, so the
     * answer is read for up to 60 seconds. An empty [message], or one longer than 500 characters, is
     * `invalidArgument`. Besides the errors of every method: `http(400)` before the device has its `did`,
     * `http(404)`, `http(429)` over the limit (10 messages a minute from a device), `http(503)` when the
     * agent did not answer — show [blank] instead; `http(402)`.
     */
    suspend fun agent(message: String, sessionId: Int? = null, locations: List<String>? = null): AgentResponse

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

    /** [agentDialog] for Java and for code outside coroutines: [listener] gets its result once, on the main thread. */
    fun agentDialog(sessionId: Int?, locations: List<String>?, listener: CompletionListener<AgentDialogResponse>)

    /** [agent] for Java and for code outside coroutines: [listener] gets its result once, on the main thread. */
    fun agent(message: String, sessionId: Int?, locations: List<String>?, listener: CompletionListener<AgentResponse>)
}
