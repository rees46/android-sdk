package com.personalization.features.products.impl

import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.personalization.Params
import com.personalization.api.OnApiCallbackListener
import com.personalization.api.managers.ProductsManager
import com.personalization.api.responses.product.Product
import com.personalization.api.responses.products.ProductsListResponse
import com.personalization.api.responses.products.counters.ProductCountersResponse
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

    override fun getProductsList(
        brands: String?,
        merchants: String?,
        categories: String?,
        locations: String?,
        limit: Int?,
        page: Int?,
        filters: Map<String, Any>?,
        onSuccess: (ProductsListResponse) -> Unit,
        onError: (Int, String?) -> Unit
    ) {
        getProductsList(
            brands = brands,
            merchants = merchants,
            categories = categories,
            locations = locations,
            limit = limit,
            page = page,
            filters = filters,
            listener = parsingListener(
                method = GET_PRODUCT_LIST_REQUEST,
                parse = { json -> Gson().fromJson(json, ProductsListResponse::class.java) ?: ProductsListResponse() },
                onSuccess = onSuccess,
                onError = onError
            )
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

    override fun getProductInfo(
        itemId: String,
        onSuccess: (Product) -> Unit,
        onError: (Int, String?) -> Unit
    ) {
        getProductInfo(
            itemId = itemId,
            listener = parsingListener(
                method = GET_PRODUCT_INFO_REQUEST,
                parse = { json ->
                    // Gson leaves non-null fields empty when the answer lacks them: no id — no product.
                    Gson().fromJson(json, Product::class.java)?.takeIf { product ->
                        val id: String? = product.id
                        !id.isNullOrEmpty()
                    }
                },
                onSuccess = onSuccess,
                onError = onError
            )
        )
    }

    override fun getProductCounters(
        item: String,
        onSuccess: (ProductCountersResponse) -> Unit,
        onError: (Int, String?) -> Unit
    ) {
        sendNetworkMethodUseCase.getAsync(
            method = GET_PRODUCT_COUNTERS_REQUEST,
            params = Params().apply { put(ITEM_KEY, item) }.build(),
            listener = object : OnApiCallbackListener() {
                override fun onSuccess(response: JSONObject?) {
                    val parsed = Gson().fromJson(
                        response.toString(),
                        ProductCountersResponse::class.java
                    )
                    onSuccess(parsed ?: ProductCountersResponse())
                }

                override fun onError(code: Int, msg: String?) {
                    onError(code, msg)
                }
            }
        )
    }

    /**
     * Parses the answer with [parse] off the raw JSON. A malformed answer, or one [parse] rejects
     * with `null`, goes to [onError] instead of throwing on the network thread.
     */
    private fun <T : Any> parsingListener(
        method: String,
        parse: (String) -> T?,
        onSuccess: (T) -> Unit,
        onError: (Int, String?) -> Unit
    ): OnApiCallbackListener = object : OnApiCallbackListener() {
        override fun onSuccess(response: JSONObject?) {
            val parsed = try {
                parse(response?.toString() ?: "{}")
            } catch (e: JsonParseException) {
                onError(PARSE_ERROR_CODE, "$method: ${e.message}")
                return
            }
            if (parsed != null) onSuccess(parsed) else onError(PARSE_ERROR_CODE, "$method: unexpected answer")
        }

        override fun onError(code: Int, msg: String?) {
            onError(code, msg)
        }
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
        const val GET_PRODUCT_COUNTERS_REQUEST = "products/counters"

        /** The answer came but could not be read as the expected model. */
        const val PARSE_ERROR_CODE = -1

        private const val ITEM_KEY = "item"

        private const val LIMIT_KEY = "limit"
        private const val PAGE_KEY = "page"
        private const val LOCATION_KEY = "locations"
        private const val BRANDS_KEY = "brands"
        private const val MERCHANTS_KEY = "merchants"
        private const val CATEGORIES_KEY = "categories"
        private const val ITEM_ID_KEY = "item_id"
        private const val FILTERS_KEY = "filters"
    }
}
