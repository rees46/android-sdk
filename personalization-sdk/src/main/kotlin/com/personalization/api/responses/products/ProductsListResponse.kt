package com.personalization.api.responses.products

import com.google.gson.annotations.SerializedName
import com.personalization.api.responses.product.Product
import com.personalization.api.responses.search.Brand
import com.personalization.api.responses.search.Category
import com.personalization.api.responses.search.PriceRange
import com.personalization.api.responses.search.PriceRanges
import com.personalization.api.responses.search.SearchFilter

/**
 * Response for the `GET /products` request.
 *
 * Has the shape of a full search and of a category: the page of products plus faceting
 * metadata, so the same classes describe it. Facets in [filters] are keyed by name.
 */
data class ProductsListResponse(
    @SerializedName("products_total")
    val productsTotal: Int = 0,
    @SerializedName("products")
    val products: List<Product> = emptyList(),
    @SerializedName("brands")
    val brands: List<Brand> = emptyList(),
    @SerializedName("filters")
    val filters: Map<String, SearchFilter>? = null,
    @SerializedName("price_range")
    val priceRange: PriceRange? = null,
    @SerializedName("categories")
    val categories: List<Category> = emptyList(),
    @SerializedName("price_ranges")
    val priceRanges: List<PriceRanges> = emptyList(),
    @SerializedName("price_median")
    val priceMedian: Double? = null
)
