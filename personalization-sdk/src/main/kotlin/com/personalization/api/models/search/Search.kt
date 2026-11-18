package com.personalization.api.models.search

import com.personalization.api.models.products.PriceInterval
import com.personalization.api.models.products.Product
import com.personalization.api.models.products.ProductCategory
import com.personalization.api.models.products.SearchFilter
import com.personalization.api.models.products.SortBy
import com.personalization.api.models.products.SortDirection
import com.personalization.api.responses.search.Brand
import com.personalization.api.responses.search.IndustrialFilters
import com.personalization.api.responses.search.Location
import com.personalization.api.responses.search.PopularItem
import com.personalization.api.responses.search.PriceRange

/*
 * What `sdk.search` asks for and answers with. In each answer the products are never null (empty when the
 * search found none; an instant or a full search's answer without them is `decoding`); any other field the
 * answer leaves out is null.
 */

/**
 * The filters, the order and the page of a full search. A field left null is not sent; a list goes as one
 * string joined with commas, and an empty list is not sent. The checks are those of
 * `ProductsListRequest`: [limit], [page], [categoryLimit] and [brandLimit] from 1, a price not below 0,
 * [priceMin] not above [priceMax]; a wrong value fails the call with `invalidArgument` before any request.
 *
 * @property excludedMerchants for marketplaces; sent only with [locations], because the server looks for
 * merchants in those locations and finds no product without them.
 * @property excludedItemIds products to leave out, such as those in the cart (`exclude`).
 * @property filters a product's parameter → the values to keep; sent as a JSON string.
 * @property sortDir sent as `order`.
 * @property disableClarification true turns the search's clarification off (`no_clarification`).
 * @property categoryLimit how many categories the facets list.
 * @property brandLimit how many brands the facets list; the server lists 1000 without it.
 */
data class SearchFullRequest(
    val categoryIds: List<String>? = null,
    val brands: List<String>? = null,
    val excludedBrands: List<String>? = null,
    val merchants: List<String>? = null,
    val excludedMerchants: List<String>? = null,
    val locations: List<String>? = null,
    val priceMin: Double? = null,
    val priceMax: Double? = null,
    val colors: List<String>? = null,
    val fashionSizes: List<String>? = null,
    val excludedItemIds: List<String>? = null,
    val filters: Map<String, List<String>>? = null,
    val sortBy: SortBy? = null,
    val sortDir: SortDirection? = null,
    val disableClarification: Boolean = false,
    val categoryLimit: Int? = null,
    val brandLimit: Int? = null,
    val limit: Int? = null,
    val page: Int? = null,
)

/**
 * What to show in an empty search field (`sdk.search.blank`).
 *
 * @property products the products the customer viewed lately ([lastProducts] true), or popular ones.
 * @property lastProducts true when [products] are the customer's own recent views; false without the field.
 * @property lastQueries the customer's recent queries.
 * @property suggests suggested queries.
 */
data class SearchBlankResponse(
    val products: List<Product> = emptyList(),
    val lastProducts: Boolean = false,
    val lastQueries: List<Query>? = null,
    val suggests: List<Query>? = null,
    val popularCategories: List<PopularItem>? = null,
    val popularBrands: List<PopularItem>? = null,
    val popularLinks: List<PopularItem>? = null,
)

/**
 * The results of a search as the customer types (`sdk.search.instant`).
 *
 * @property searchQuery the query after the server fixed its keyboard layout and typos.
 * @property searchQueryOriginal the query as it came.
 * @property clarification whether the search's clarification worked.
 * @property queries suggested queries.
 * @property redirect the page the shop sends this query to instead of the results.
 */
data class SearchInstantResponse(
    val searchQuery: String? = null,
    val searchQueryOriginal: String? = null,
    val clarification: Boolean? = null,
    val productsTotal: Int? = null,
    val products: List<Product> = emptyList(),
    val categories: List<ProductCategory>? = null,
    val queries: List<Query>? = null,
    val locations: List<Location>? = null,
    val redirect: SearchRedirect? = null,
)

/** The screen of a search's results (`sdk.search.full`); the facets as in `ProductsListResponse`. */
data class SearchFullResponse(
    val searchQuery: String? = null,
    val searchQueryOriginal: String? = null,
    val clarification: Boolean? = null,
    val productsTotal: Int? = null,
    val products: List<Product> = emptyList(),
    val categories: List<ProductCategory>? = null,
    val brands: List<Brand>? = null,
    val locations: List<Location>? = null,
    val filters: Map<String, SearchFilter>? = null,
    val industrialFilters: IndustrialFilters? = null,
    val priceRange: PriceRange? = null,
    val priceRanges: List<PriceInterval>? = null,
    val priceMedian: Double? = null,
)

/**
 * A suggested or a recent query.
 *
 * @property url the link of a full search by it.
 * @property score only among the suggestions of an instant search.
 */
data class Query(
    val name: String,
    val url: String? = null,
    val score: Double? = null,
)

/**
 * A query the shop sends to a page instead of the results.
 *
 * @property url the page (`redirect_link`).
 * @property deeplink the page in the app (`deep_link`), when the shop set one.
 */
data class SearchRedirect(
    val query: String? = null,
    val url: String? = null,
    val deeplink: String? = null,
)
