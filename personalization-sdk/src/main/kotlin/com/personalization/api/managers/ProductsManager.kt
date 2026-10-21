package com.personalization.api.managers

import com.personalization.api.OnApiCallbackListener
import com.personalization.api.responses.product.Product
import com.personalization.api.responses.products.ProductsListResponse
import com.personalization.api.responses.products.counters.ProductCountersResponse

interface ProductsManager {

    fun getProductsList(
        brands: String?,
        merchants: String?,
        categories: String?,
        locations: String?,
        limit: Int?,
        page: Int?,
        filters: Map<String, Any>?,
        listener: OnApiCallbackListener? = null
    )

    /**
     * Fetch a product listing (`GET /products`) parsed into [ProductsListResponse].
     *
     * Same request as the listener-based [getProductsList]; the answer has the shape of a full
     * search: products, total and facets.
     *
     * @param brands Comma-separated brand filter
     * @param merchants Comma-separated merchant filter
     * @param categories Comma-separated category filter
     * @param locations Comma-separated locations filter
     * @param limit Max products to return
     * @param page Page number
     * @param filters Arbitrary facet filters
     * @param onSuccess Callback with the parsed listing
     * @param onError Callback for error
     */
    fun getProductsList(
        brands: String? = null,
        merchants: String? = null,
        categories: String? = null,
        locations: String? = null,
        limit: Int? = null,
        page: Int? = null,
        filters: Map<String, Any>? = null,
        onSuccess: (ProductsListResponse) -> Unit,
        onError: (Int, String?) -> Unit = { _: Int, _: String? -> }
    )

    fun getProductInfo(
        itemId: String,
        listener: OnApiCallbackListener?
    )

    /**
     * Fetch one product (`GET /products/get`) parsed into [Product].
     *
     * Same request as the listener-based [getProductInfo]. An answer that is not a product
     * goes to [onError].
     *
     * @param itemId Product item id
     * @param onSuccess Callback with the parsed product
     * @param onError Callback for error
     */
    fun getProductInfo(
        itemId: String,
        onSuccess: (Product) -> Unit,
        onError: (Int, String?) -> Unit = { _: Int, _: String? -> }
    )

    /**
     * Fetch view/cart/purchase counters and trigger counts for a product
     * (`GET /products/counters`).
     *
     * @param item Product item id
     * @param onSuccess Callback with the parsed counters
     * @param onError Callback for error
     */
    fun getProductCounters(
        item: String,
        onSuccess: (ProductCountersResponse) -> Unit,
        onError: (Int, String?) -> Unit = { _: Int, _: String? -> }
    )
}
