package com.personalizatio.entities.categories.category

import com.google.gson.annotations.SerializedName
import com.personalizatio.entities.products.Product

data class CategoryEntity(
    @SerializedName("brands")
    val brands: List<Brand>,
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
    val products: List<Product>,
    @SerializedName("products_total")
    val productsTotal: Int
)