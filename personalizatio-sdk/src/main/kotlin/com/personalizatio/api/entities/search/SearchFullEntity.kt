package com.personalizatio.api.entities.search

import com.google.gson.annotations.SerializedName
import com.personalizatio.api.entities.product.ProductEntity

data class SearchFullEntity(
    @SerializedName("brands")
    val brands: List<BrandEntity>,
    @SerializedName("categories")
    val categories: List<CategoryEntity>,
    @SerializedName("clarification")
    val clarification: Boolean,
    @SerializedName("collections")
    val collections: List<Any>,
    @SerializedName("html")
    val html: String,
    @SerializedName("price_median")
    val priceMedian: Double,
    @SerializedName("price_range")
    val priceRange: PriceRangeEntity,
    @SerializedName("price_ranges")
    val priceRanges: List<PriceRangesEntity>,
    @SerializedName("products")
    val products: List<ProductEntity>,
    @SerializedName("products_total")
    val productsTotal: Int,
    @SerializedName("requests_count")
    val requestsCount: Int,
    @SerializedName("search_query")
    val searchQuery: String
)
