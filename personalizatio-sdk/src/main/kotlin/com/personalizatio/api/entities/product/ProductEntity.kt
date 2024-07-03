package com.personalizatio.api.entities.product

import com.google.gson.annotations.SerializedName

data class ProductEntity(
    @SerializedName("brand")
    val brand: String,
    @SerializedName("categories")
    val categories: List<CategoryEntity>,
    @SerializedName("category_ids")
    val categoryIds: List<String>,
    @SerializedName("currency")
    val currency: String,
    @SerializedName("description")
    val description: String,
    @SerializedName("_id")
    val _id: String,
    @SerializedName("id")
    val id: String,
    @SerializedName("image_url")
    val imageUrl: String,
    @SerializedName("image_url_handle")
    val imageUrlHandle: String,
    @SerializedName("image_url_resized")
    val imageUrlResized: ImageUrlResizedEntity,
    @SerializedName("name")
    val name: String,
    @SerializedName("picture")
    val picture: String,
    @SerializedName("price")
    val price: Double,
    @SerializedName("price_formatted")
    val priceFormatted: String,
    @SerializedName("price_full")
    val priceFull: Double,
    @SerializedName("price_full_formatted")
    val priceFullFormatted: String,
    @SerializedName("relative_sales_rate")
    val relativeSalesRate: Double,
    @SerializedName("sales_rate")
    val salesRate: Int,
    @SerializedName("url")
    val url: String,
    @SerializedName("url_handle")
    val urlHandle: String
)
