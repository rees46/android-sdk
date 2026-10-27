package com.personalization.features.category.impl

import com.google.gson.Gson
import com.personalization.Params
import com.personalization.api.OnApiCallbackListener
import com.personalization.api.managers.CategoryManager
import com.personalization.api.responses.category.CategoryResponse
import com.personalization.sdk.domain.usecases.network.SendNetworkMethodUseCase
import java.math.BigDecimal
import javax.inject.Inject
import org.json.JSONObject

internal class CategoryManagerImpl @Inject constructor(
    private val sendNetworkMethodUseCase: SendNetworkMethodUseCase
) : CategoryManager {

    override fun getCategory(
        category: String,
        limit: Int?,
        page: Int?,
        brands: String?,
        locations: String?,
        filters: Map<String, Any>?,
        onSuccess: (CategoryResponse) -> Unit,
        onError: (Int, String?) -> Unit
    ) {
        getCategory(
            category, limit, page, brands, locations, filters,
            priceMin = null, priceMax = null, sortBy = null, sortDir = null,
            onSuccess = onSuccess, onError = onError
        )
    }

    override fun getCategory(
        category: String,
        limit: Int?,
        page: Int?,
        brands: String?,
        locations: String?,
        filters: Map<String, Any>?,
        priceMin: Double?,
        priceMax: Double?,
        sortBy: String?,
        sortDir: String?,
        onSuccess: (CategoryResponse) -> Unit,
        onError: (Int, String?) -> Unit
    ) {
        val params = Params()
        limit?.let { params.put(LIMIT_KEY, it) }
        page?.let { params.put(PAGE_KEY, it) }
        brands?.let { params.put(BRANDS_KEY, it) }
        locations?.let { params.put(LOCATIONS_KEY, it) }
        filters?.takeIf { it.isNotEmpty() }?.let {
            params.put(FILTERS_KEY, JSONObject(it).toString())
        }
        priceMin?.let { params.put(PRICE_MIN_KEY, plainNumber(it)) }
        priceMax?.let { params.put(PRICE_MAX_KEY, plainNumber(it)) }
        sortBy?.let { params.put(SORT_BY_KEY, it) }
        sortDir?.let { params.put(SORT_DIR_KEY, it) }

        sendNetworkMethodUseCase.getAsync(
            "$CATEGORY_REQUEST/$category",
            params.build(),
            object : OnApiCallbackListener() {
                override fun onSuccess(response: JSONObject?) {
                    val parsed = Gson().fromJson(
                        response.toString(),
                        CategoryResponse::class.java
                    )
                    onSuccess(parsed ?: CategoryResponse())
                }

                override fun onError(code: Int, msg: String?) {
                    onError(code, msg)
                }
            }
        )
    }

    companion object {
        const val CATEGORY_REQUEST = "category"

        private const val LIMIT_KEY = "limit"
        private const val PAGE_KEY = "page"
        private const val BRANDS_KEY = "brands"
        private const val LOCATIONS_KEY = "locations"
        private const val FILTERS_KEY = "filters"
        private const val PRICE_MIN_KEY = "price_min"
        private const val PRICE_MAX_KEY = "price_max"
        private const val SORT_BY_KEY = "sort_by"
        private const val SORT_DIR_KEY = "sort_dir"

        /** `1000.0` → `1000`, `1.5E7` → `15000000`: a plain number, without the exponent and a trailing `.0`. */
        private fun plainNumber(value: Double): String =
            if (value.isFinite()) BigDecimal.valueOf(value).stripTrailingZeros().toPlainString() else value.toString()
    }
}
