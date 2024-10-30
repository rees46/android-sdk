package com.personalization.sdk.domain.models.products

data class Product(
    val brand: String,
    val currency: String,
    val id: String,
    val isNew: Boolean? = null,
    val name: String,
    val oldPrice: String = "0",
    val price: Double,
    val priceFormatted: String,
    val priceFullFormatted: String,
    val picture: String,
    val url: String,
    val description: String,
    val categoryIds: List<String>,
    val fashionFeature: String?,
    val fashionGender: String?,
    val salesRate: Int,
    val relativeSalesRate: Int,
    val imageUrl: String,
    val imageUrlHandle: String,
    val imageUrlResized: ImageUrlResized,
    val internalId: String,
    val groupId: String,
    val barcode: String,
    val categories: List<ProductCategory>
)