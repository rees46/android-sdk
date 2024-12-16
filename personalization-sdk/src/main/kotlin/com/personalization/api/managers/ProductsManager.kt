package com.personalization.api.managers

import com.personalization.api.OnApiCallbackListener

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

    fun getClientShoppingCart(
        listener: OnApiCallbackListener?
    )
}
