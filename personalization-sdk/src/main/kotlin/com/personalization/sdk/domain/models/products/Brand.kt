package com.personalization.sdk.domain.models.products

data class Brand(
    val name: String,
    val picture: String,
    val count: Int
)

data class Category(
    val id: String,
    val name: String,
    val url: String,
    val urlHandle: String,
    val count: Int,
    val parent: String?,
    val alias: String?
)

data class FilterValue(
    val value: String,
    val count: Int
)

data class Filter(
    val filter: FilterDetails
)

data class FilterDetails(
    val count: Int,
    val priority: Int,
    val ranges: List<Int>?,
    val values: List<FilterValue>
)

data class PriceRange(
    val min: Double,
    val max: Double
)

data class ProductCategory(
    val id: String,
    val name: String,
    val parent: String?,
    val params: List<ProductParam>
)

data class ProductParam(
    val key: String,
    val values: List<String>
)

data class ImageUrlResized(
    val sizeToPath: Map<String, String>
)





