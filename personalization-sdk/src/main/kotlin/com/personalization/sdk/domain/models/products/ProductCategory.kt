package com.personalization.sdk.domain.models.products

data class ProductCategory(
    val id: String,
    val name: String,
    val parent: String?,
    val params: List<ProductParam>
)