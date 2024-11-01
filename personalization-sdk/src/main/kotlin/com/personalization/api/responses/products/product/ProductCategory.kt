package com.personalization.api.responses.products.product

data class ProductCategory(
    val id: String,
    val name: String,
    val parent: String?,
    val params: List<ProductParam>
)