package com.personalization.features.recommendation

import com.personalization.api.managers.RecommendationsApi
import com.personalization.api.models.recommendations.RecommendationRequest
import com.personalization.api.models.recommendations.RecommendationResponse
import com.personalization.core.BridgeApi
import com.personalization.core.CompletionListener
import com.personalization.core.internal.NamespaceCalls
import com.personalization.core.internal.arrayOrNull
import com.personalization.core.internal.intOrNull
import com.personalization.core.internal.invalidArgument
import com.personalization.core.internal.mapObjects
import com.personalization.core.internal.nonBlankStringOrNull
import com.personalization.core.internal.pathSegment
import com.personalization.core.internal.putIfNotNull
import com.personalization.core.internal.putList
import com.personalization.core.internal.requireAtLeast
import com.personalization.core.internal.requireNotBlank
import com.personalization.core.internal.stringOrNull
import com.personalization.core.internal.strings
import com.personalization.features.products.CatalogJson
import com.personalization.sdk.domain.models.NetworkMethod
import org.json.JSONObject

/** [RecommendationsApi] over the namespaces' path ([NamespaceCalls]). */
@OptIn(BridgeApi::class)
internal class RecommendationsApiImpl(private val calls: NamespaceCalls) : RecommendationsApi {

    override suspend fun get(code: String, request: RecommendationRequest): RecommendationResponse {
        val (path, params) = calls.checked(GET) { PATH + pathSegment(requireNotBlank(code, "code")) to params(request) }
        return calls.request(GET, NetworkMethod.GET(path), params, read = ::readRecommendations)
    }

    override fun get(code: String, request: RecommendationRequest, listener: CompletionListener<RecommendationResponse>) =
        calls.complete(listener) { get(code, request) }

    internal companion object {
        const val GET = "recommendations.get"
        const val PATH = "recommend/"

        /** The request's fields: `extended` and `with_locations` go only when true. */
        private fun params(request: RecommendationRequest): JSONObject = with(request) {
            requireAtLeast(limit, 1, "limit")
            if (withLocations && !extended) invalidArgument("withLocations needs extended")
            JSONObject()
                .putIfNotNull("item_id", itemId)
                .putIfNotNull("category", categoryId)
                .putIfNotNull("search_query", searchQuery)
                .putList("exclude", excludedItemIds)
                .putList("brands", brands)
                .putList("locations", locations)
                .putIfNotNull("limit", limit)
                .putIfNotNull("extended", "true".takeIf { extended })
                .putIfNotNull("with_locations", "true".takeIf { withLocations })
        }

        /**
         * The block's products. `recommends` holds whole products (`extended`) or their ids; [itemIds] is
         * filled either way. A block on pause answers `{"state": "paused"}` without `recommends`: no
         * products. Any other answer without `recommends` is null (`decoding`).
         */
        fun readRecommendations(body: Any?): RecommendationResponse? {
            if (body !is JSONObject) return null
            val recommends = body.arrayOrNull(RECOMMENDS)
            if (recommends == null && body.stringOrNull("state") != PAUSED) return null
            // Whole products, or their ids alone (strings, or numbers from an older server).
            val products = recommends?.mapObjects { CatalogJson.product(it) }.orEmpty()
            val itemIds = products.map { it.id }.ifEmpty { recommends?.strings()?.filter { it.isNotBlank() }.orEmpty() }
            return RecommendationResponse(
                id = body.intOrNull("id"),
                title = body.nonBlankStringOrNull("title"),
                link = body.nonBlankStringOrNull("link"),
                products = products,
                itemIds = itemIds,
            )
        }

        private const val RECOMMENDS = "recommends"
        private const val PAUSED = "paused"
    }
}
