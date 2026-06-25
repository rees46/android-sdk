package com.personalization.api.responses.category

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import com.personalization.api.responses.product.Product
import com.personalization.api.responses.search.Brand
import com.personalization.api.responses.search.Category
import com.personalization.api.responses.search.PriceRange
import com.personalization.api.responses.search.PriceRanges

/**
 * Response for the `GET /category/{category}` request.
 *
 * Mirrors the full-search payload: the matched products plus faceting metadata
 * (brands, price ranges, nested categories). The raw `filters` object is kept as
 * a [JsonObject] because its shape is shop-specific.
 */
data class CategoryResponse(
    @SerializedName("products_total")
    val productsTotal: Int = 0,
    @SerializedName("products")
    val products: List<Product> = emptyList(),
    @SerializedName("brands")
    val brands: List<Brand> = emptyList(),
    @SerializedName("categories")
    val categories: List<Category> = emptyList(),
    @SerializedName("price_range")
    val priceRange: PriceRange? = null,
    @SerializedName("price_ranges")
    val priceRanges: List<PriceRanges> = emptyList(),
    @SerializedName("price_median")
    val priceMedian: Double? = null,
    @SerializedName("filters")
    val filters: JsonObject? = null
)
