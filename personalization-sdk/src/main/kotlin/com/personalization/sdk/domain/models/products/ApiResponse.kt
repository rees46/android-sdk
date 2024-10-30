package com.personalization.sdk.domain.models.products

data class ApiResponse(
    val brands: List<Brand>,
    val categories: List<Category>,
    val filters: List<Filter>,
    val priceRange: PriceRange,
    val products: List<Product>,
    val productsTotal: Int,
    val priceRanges: List<PriceRangeItem>,
    val priceMedian: Double
)
