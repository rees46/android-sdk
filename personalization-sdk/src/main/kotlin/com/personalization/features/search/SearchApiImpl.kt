package com.personalization.features.search

import com.personalization.api.managers.SearchApi
import com.personalization.api.models.search.Query
import com.personalization.api.models.search.SearchBlankResponse
import com.personalization.api.models.search.SearchFullRequest
import com.personalization.api.models.search.SearchFullResponse
import com.personalization.api.models.search.SearchInstantResponse
import com.personalization.api.models.search.SearchRedirect
import com.personalization.core.BridgeApi
import com.personalization.core.CompletionListener
import com.personalization.core.internal.NamespaceCalls
import com.personalization.core.internal.checked
import com.personalization.core.internal.arrayOrNull
import com.personalization.core.internal.booleanOrNull
import com.personalization.core.internal.doubleOrNull
import com.personalization.core.internal.intOrNull
import com.personalization.core.internal.mapObjects
import com.personalization.core.internal.objectOrNull
import com.personalization.core.internal.putFilters
import com.personalization.core.internal.putIfNotNull
import com.personalization.core.internal.putList
import com.personalization.core.internal.putNumber
import com.personalization.core.internal.requireAtLeast
import com.personalization.core.internal.requireNotBlank
import com.personalization.core.internal.requirePriceRange
import com.personalization.core.internal.stringOrNull
import com.personalization.features.products.CatalogJson
import com.personalization.sdk.domain.models.NetworkMethod
import org.json.JSONArray
import org.json.JSONObject

/** [SearchApi] over the namespaces' path ([NamespaceCalls]). */
@OptIn(BridgeApi::class)
internal class SearchApiImpl(private val calls: NamespaceCalls) : SearchApi {

    override suspend fun blank(): SearchBlankResponse =
        calls.request(BLANK, NetworkMethod.GET(BLANK_PATH), read = ::readBlank)

    override suspend fun instant(
        query: String,
        locations: List<String>?,
        excludedMerchants: List<String>?,
        excludedBrands: List<String>?,
    ): SearchInstantResponse {
        val params = calls.checked(INSTANT) {
            JSONObject()
                .put(TYPE, INSTANT_SEARCH)
                .put(SEARCH_QUERY, requireNotBlank(query, "query"))
                .putList("locations", locations)
                .putList("excluded_merchants", excludedMerchants.takeUnless { locations.isNullOrEmpty() })
                .putList("excluded_brands", excludedBrands)
        }
        return calls.request(INSTANT, NetworkMethod.GET(SEARCH_PATH), params, read = ::readInstant)
    }

    override suspend fun full(query: String, request: SearchFullRequest): SearchFullResponse {
        val params = calls.checked(FULL) {
            fullParams(request, JSONObject().put(TYPE, FULL_SEARCH).put(SEARCH_QUERY, requireNotBlank(query, "query")))
        }
        return calls.request(FULL, NetworkMethod.GET(SEARCH_PATH), params, read = ::readFull)
    }

    override fun blank(listener: CompletionListener<SearchBlankResponse>) = calls.complete(listener) { blank() }

    override fun instant(
        query: String,
        locations: List<String>?,
        excludedMerchants: List<String>?,
        excludedBrands: List<String>?,
        listener: CompletionListener<SearchInstantResponse>,
    ) = calls.complete(listener) { instant(query, locations, excludedMerchants, excludedBrands) }

    override fun full(query: String, request: SearchFullRequest, listener: CompletionListener<SearchFullResponse>) =
        calls.complete(listener) { full(query, request) }

    internal companion object {
        const val BLANK = "search.blank"
        const val INSTANT = "search.instant"
        const val FULL = "search.full"
        const val BLANK_PATH = "search/blank"
        const val SEARCH_PATH = "search"
        private const val TYPE = "type"
        private const val SEARCH_QUERY = "search_query"
        private const val INSTANT_SEARCH = "instant_search"
        private const val FULL_SEARCH = "full_search"

        /** The fields of [request] after their checks; the sort's direction goes as `order`, as the API's documentation names it. */
        private fun fullParams(request: SearchFullRequest, params: JSONObject): JSONObject = with(request) {
            requirePriceRange(priceMin, priceMax)
            requireAtLeast(categoryLimit, 1, "categoryLimit")
            requireAtLeast(brandLimit, 1, "brandLimit")
            requireAtLeast(limit, 1, "limit")
            requireAtLeast(page, 1, "page")
            params
                .putList("categories", categoryIds)
                .putList("brands", brands)
                .putList("excluded_brands", excludedBrands)
                .putList("merchants", merchants)
                .putList("excluded_merchants", excludedMerchants.takeUnless { locations.isNullOrEmpty() })
                .putList("locations", locations)
                .putNumber("price_min", priceMin)
                .putNumber("price_max", priceMax)
                .putList("colors", colors)
                .putList("fashion_sizes", fashionSizes)
                .putList("exclude", excludedItemIds)
                .putFilters("filters", filters)
                .putIfNotNull("sort_by", sortBy?.value)
                .putIfNotNull("order", sortDir?.value)
                .putIfNotNull("no_clarification", "true".takeIf { disableClarification })
                .putIfNotNull("category_limit", categoryLimit)
                .putIfNotNull("brand_limit", brandLimit)
                .putIfNotNull("limit", limit)
                .putIfNotNull("page", page)
        }

        /**
         * An empty search. The server leaves out what is empty, and answers `[]` when everything is: so an
         * empty array, or no body, is an empty result.
         */
        fun readBlank(body: Any?): SearchBlankResponse? {
            if (body == null || (body is JSONArray && body.length() == 0)) return SearchBlankResponse()
            if (body !is JSONObject) return null
            return SearchBlankResponse(
                products = CatalogJson.products(body).orEmpty(),
                lastProducts = body.booleanOrNull("last_products") ?: false,
                lastQueries = queries(body, "last_queries"),
                suggests = queries(body, "suggests"),
                popularCategories = CatalogJson.popularItems(body, "popular_categories"),
                popularBrands = CatalogJson.popularItems(body, "popular_brands"),
                popularLinks = CatalogJson.popularItems(body, "popular_links"),
            )
        }

        fun readInstant(body: Any?): SearchInstantResponse? {
            if (body !is JSONObject) return null
            return SearchInstantResponse(
                searchQuery = body.stringOrNull("search_query"),
                searchQueryOriginal = body.stringOrNull("search_query_original"),
                clarification = body.booleanOrNull("clarification"),
                productsTotal = body.intOrNull("products_total"),
                products = CatalogJson.products(body).orEmpty(),
                categories = CatalogJson.categories(body),
                queries = queries(body, "queries"),
                locations = CatalogJson.locations(body),
                redirect = body.objectOrNull("search_query_redirects")?.let { redirect ->
                    SearchRedirect(
                        query = redirect.stringOrNull("query"),
                        url = redirect.stringOrNull("redirect_link"),
                        deeplink = redirect.stringOrNull("deep_link"),
                    )
                },
            )
        }

        fun readFull(body: Any?): SearchFullResponse? {
            if (body !is JSONObject) return null
            return SearchFullResponse(
                searchQuery = body.stringOrNull("search_query"),
                searchQueryOriginal = body.stringOrNull("search_query_original"),
                clarification = body.booleanOrNull("clarification"),
                productsTotal = body.intOrNull("products_total"),
                products = CatalogJson.products(body).orEmpty(),
                categories = CatalogJson.categories(body),
                brands = CatalogJson.brands(body),
                locations = CatalogJson.locations(body),
                filters = CatalogJson.filters(body),
                industrialFilters = CatalogJson.industrialFilters(body),
                priceRange = CatalogJson.priceRange(body),
                priceRanges = CatalogJson.priceRanges(body),
                priceMedian = body.doubleOrNull("price_median"),
            )
        }

        /** Queries; one without a name is left out. */
        private fun queries(json: JSONObject, key: String): List<Query>? = json.arrayOrNull(key)?.mapObjects { query ->
            Query(
                name = query.stringOrNull("name") ?: return@mapObjects null,
                url = query.stringOrNull("url"),
                score = query.doubleOrNull("score"),
            )
        }
    }
}
