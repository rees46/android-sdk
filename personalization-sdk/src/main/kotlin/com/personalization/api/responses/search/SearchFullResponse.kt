package com.personalization.api.responses.search

import com.google.gson.annotations.SerializedName
import com.personalization.api.responses.product.Product

data class SearchFullResponse(
    val brands: List<Brand>,
    val categories: List<Category>,
    val clarification: Boolean,
    val collections: List<Any>,
    val html: String,
    @SerializedName("price_median")
    val priceMedian: Double,
    @SerializedName("price_range")
    val priceRange: PriceRange,
    @SerializedName("price_ranges")
    val priceRanges: List<PriceRanges>,
    val products: List<Product>,
    val locations: List<Location> = emptyList(),
    @SerializedName("products_total")
    val productsTotal: Int,
    @SerializedName("requests_count")
    val requestsCount: Int,
    @SerializedName("search_query")
    val searchQuery: String,
    /** Facets keyed by name, e.g. `merchant`, `material`. Absent when the shop has none. */
    @SerializedName("filters")
    val filters: Map<String, SearchFilter>? = null,
    @SerializedName("industrial_filters")
    val industrialFilters: IndustrialFilters? = null
)
