package com.personalization.api.models.products

/*
 * What `sdk.products.getList` and `getCategory` ask for. A field left null is not sent. A list goes as one
 * string joined with commas, and an empty list is not sent. A wrong value fails the call with
 * `invalidArgument` before any request: [ProductsListRequest.limit] or [ProductsListRequest.page] below 1,
 * a negative price, `priceMin` above `priceMax`, or [SortBy.RELEVANCE], which only a search sorts by.
 */

/**
 * The filters, the order and the page of the catalog's listing.
 *
 * @property merchants for marketplaces; with [locations], the merchants are looked for in those locations.
 * @property locations products in stock in these locations.
 * @property priceMin not below 0; the server drops the fraction.
 * @property priceMax not below [priceMin]; the server drops the fraction.
 * @property filters a product's parameter → the values to keep; sent as a JSON string.
 * @property limit products on a page, from 1.
 * @property page the page, from 1.
 */
data class ProductsListRequest(
    val categoryIds: List<String>? = null,
    val brands: List<String>? = null,
    val merchants: List<String>? = null,
    val locations: List<String>? = null,
    val priceMin: Double? = null,
    val priceMax: Double? = null,
    val filters: Map<String, List<String>>? = null,
    val sortBy: SortBy? = null,
    val sortDir: SortDirection? = null,
    val limit: Int? = null,
    val page: Int? = null,
)

/** The filters, the order and the page of a category's screen; the fields as in [ProductsListRequest]. */
data class CategoryRequest(
    val brands: List<String>? = null,
    val locations: List<String>? = null,
    val filters: Map<String, List<String>>? = null,
    val priceMin: Double? = null,
    val priceMax: Double? = null,
    val sortBy: SortBy? = null,
    val sortDir: SortDirection? = null,
    val limit: Int? = null,
    val page: Int? = null,
)

/**
 * What a listing or a search sorts by (`sort_by`). An open set: the server may add a key, and an app passes
 * it as `SortBy("key")` without a new release of the SDK.
 */
@JvmInline
value class SortBy(val value: String) {
    companion object {
        val PRICE = SortBy("price")
        val DISCOUNT = SortBy("discount")
        val SALES_RATE = SortBy("sales_rate")
        val DATE = SortBy("date")
        val PRICE_MARGIN = SortBy("price_margin")

        /** The server applies it only together with another key, which the SDK cannot send yet. */
        val RATING = SortBy("rating")

        /** Only for `sdk.search.full`; a listing or a category fails with `invalidArgument`. */
        val RELEVANCE = SortBy("relevance")
    }
}

/** The direction of a sort; the server sorts in [DESC] order when the request names none. */
enum class SortDirection(val value: String) {
    ASC("asc"),
    DESC("desc"),
}
