package com.personalization.api.responses.products.product

import com.google.gson.annotations.SerializedName
import com.personalization.api.responses.products.brand.Brand
import com.personalization.api.responses.products.category.Category
import com.personalization.api.responses.products.filter.Filter
import com.personalization.api.responses.products.price.PriceRange
import com.personalization.api.responses.products.price.PriceRangeItem

data class ProductsResponse(
    @SerializedName("brands")
    val brands: List<Brand>,
    @SerializedName("categories")
    val categories: List<Category>,
    @SerializedName("filters")
    val filters: List<Filter>,
    @SerializedName("price_range")
    val priceRange: PriceRange,
    @SerializedName("products")
    val products: List<Product>,
    @SerializedName("products_total")
    val productsTotal: Int,
    @SerializedName("price_ranges")
    val priceRanges: List<PriceRangeItem>,
    @SerializedName("price_median")
    val priceMedian: Double
)
