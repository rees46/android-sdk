package com.personalization.api.managers

import com.personalization.api.OnApiCallbackListener
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

    fun getProductInfo(
        itemId: String,
        listener: OnApiCallbackListener?
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
