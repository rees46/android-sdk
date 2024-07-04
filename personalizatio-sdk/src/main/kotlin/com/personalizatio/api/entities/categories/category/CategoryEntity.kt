package com.personalizatio.api.entities.categories.category

import com.google.gson.annotations.SerializedName
import com.personalizatio.api.entities.product.BrandEntity
import com.personalizatio.api.entities.product.ProductEntity

data class CategoryEntity(
    @SerializedName("brands")
    val brands: List<BrandEntity>,
    @SerializedName("categories")
    val categories: List<Category>,
    @SerializedName("html")
    val html: String,
    @SerializedName("price_median")
    val priceMedian: Double,
    @SerializedName("price_range")
    val priceRange: PriceRange,
    @SerializedName("price_ranges")
    val priceRanges: List<PriceRanges>,
    @SerializedName("products")
    val products: List<ProductEntity>,
    @SerializedName("products_total")
    val productsTotal: Int
)
