package com.personalization.api.managers

import com.personalization.api.responses.category.CategoryResponse

interface CategoryManager {

    /**
     * Fetch a category listing (`GET /category/{category}`).
     *
     * The shop and device are identified automatically by the SDK's configured
     * `shop_id` / `did`. [category] is the category slug (not a numeric id).
     *
     * @param category Category slug (path segment)
     * @param limit Max products to return
     * @param page Page number
     * @param brands Comma-separated brand filter
     * @param locations Comma-separated locations filter
     * @param filters Arbitrary facet filters
     * @param onSuccess Callback with the parsed category response
     * @param onError Callback for error
     */
    fun getCategory(
        category: String,
        limit: Int? = null,
        page: Int? = null,
        brands: String? = null,
        locations: String? = null,
        filters: Map<String, Any>? = null,
        onSuccess: (CategoryResponse) -> Unit,
        onError: (Int, String?) -> Unit = { _: Int, _: String? -> }
    )

    /**
     * Fetch a category listing (`GET /category/{category}`) with a price range and sorting on
     * top of the filters of the variant above.
     *
     * Has a default body that falls back to the variant above without the price range and the
     * sorting, so implementations of this interface written before it keep compiling; the
     * SDK's own implementation sends them.
     *
     * @param category Category slug (path segment)
     * @param limit Max products to return
     * @param page Page number
     * @param brands Comma-separated brand filter
     * @param locations Comma-separated locations filter
     * @param filters Arbitrary facet filters, e.g. `mapOf("Цвет" to listOf("Белый"))`
     * @param priceMin Lowest price
     * @param priceMax Highest price
     * @param sortBy `popular`, `price`, `discount`, `sales_rate` or `date`; `null` — relevance
     * @param sortDir `asc` or `desc`
     * @param onSuccess Callback with the parsed category response
     * @param onError Callback for error
     */
    fun getCategory(
        category: String,
        limit: Int? = null,
        page: Int? = null,
        brands: String? = null,
        locations: String? = null,
        filters: Map<String, Any>? = null,
        priceMin: Double? = null,
        priceMax: Double? = null,
        sortBy: String? = null,
        sortDir: String? = null,
        onSuccess: (CategoryResponse) -> Unit,
        onError: (Int, String?) -> Unit = { _: Int, _: String? -> }
    ) {
        getCategory(category, limit, page, brands, locations, filters, onSuccess, onError)
    }
}
