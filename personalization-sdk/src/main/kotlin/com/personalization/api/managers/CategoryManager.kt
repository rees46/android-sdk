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
}
