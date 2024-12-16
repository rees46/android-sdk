package com.personalization.features.products.impl

import com.personalization.Params
import com.personalization.api.OnApiCallbackListener
import com.personalization.api.managers.ProductsManager
import com.personalization.sdk.domain.usecases.network.SendNetworkMethodUseCase
import org.json.JSONObject
import javax.inject.Inject

internal class ProductsManagerImpl @Inject constructor(
    private val sendNetworkMethodUseCase: SendNetworkMethodUseCase,
) : ProductsManager {

    override fun getProductsList(
        brands: String?,
        merchants: String?,
        categories: String?,
        locations: String?,
        limit: Int?,
        page: Int?,
        filters: Map<String, Any>?,
        listener: OnApiCallbackListener?
    ) {
        sendNetworkMethodUseCase.getAsync(
            method = GET_PRODUCT_LIST_REQUEST,
            params = Params().buildParams(
                brands = brands,
                merchants = merchants,
                categories = categories,
                locations = locations,
                limit = limit,
                page = page,
                filters = filters,
            ).build(),
            listener = listener
        )
    }

    override fun getProductInfo(
        itemId: String,
        listener: OnApiCallbackListener?
    ) {
        sendNetworkMethodUseCase.getAsync(
            method = GET_PRODUCT_INFO_REQUEST,
            params = Params().buildParams(
                itemId = itemId,
            ).build(),
            listener = listener
        )
    }

    override fun getClientShoppingCart(listener: OnApiCallbackListener?) {
        sendNetworkMethodUseCase.getAsync(
            method = GET_CLIENT_SHOPPING_CART,
            params = Params().build(),
            listener = listener
        )
    }

    private fun Params.buildParams(
        itemId: String? = null,
        brands: String? = null,
        merchants: String? = null,
        categories: String? = null,
        locations: String? = null,
        limit: Int? = null,
        page: Int? = null,
        filters: Map<String, Any>? = null,
    ): Params = this.apply {
        itemId?.let { put(ITEM_ID_KEY, it) }
        limit?.let { put(LIMIT_KEY, it) }
        page?.let { put(PAGE_KEY, it) }
        locations?.let { put(LOCATION_KEY, it) }
        brands?.let { put(BRANDS_KEY, it) }
        merchants?.let { put(MERCHANTS_KEY, it) }
        categories?.let { put(CATEGORIES_KEY, it) }

        filters?.takeIf { it.isNotEmpty() }?.let {
            val filtersJson = JSONObject(it).toString()
            put(FILTERS_KEY, filtersJson)
        }
    }

    companion object {
        const val GET_PRODUCT_LIST_REQUEST = "products"
        const val GET_PRODUCT_INFO_REQUEST = "products/get"
        const val GET_CLIENT_SHOPPING_CART = "products/cart"

        private const val LIMIT_KEY = "limit"
        private const val PAGE_KEY = "page"
        private const val LOCATION_KEY = "locations"
        private const val BRANDS_KEY = "brands"
        private const val MERCHANTS_KEY = "merchants"
        private const val CATEGORIES_KEY = "categories"
        private const val ITEM_ID_KEY = "item_id"
        private const val FILTERS_KEY = ""
    }
}
