package com.personalization.api.responses.products

import com.google.gson.annotations.SerializedName
import com.personalization.api.responses.product.ImageUrlResized

data class Product(
    @SerializedName("brand")
    val brand: String,
    @SerializedName("currency")
    val currency: String,
    @SerializedName("id")
    val id: String,
    @SerializedName("is_new")
    val isNew: Boolean? = null,
    @SerializedName("name")
    val name: String,
    @SerializedName("old_price")
    val oldPrice: String = "0",
    @SerializedName("price")
    val price: Double,
    @SerializedName("price_formatted")
    val priceFormatted: String,
    @SerializedName("price_full_formatted")
    val priceFullFormatted: String,
    @SerializedName("picture")
    val picture: String,
    @SerializedName("url")
    val url: String,
    @SerializedName("description")
    val description: String,
    @SerializedName("category_ids")
    val categoryIds: List<String>,
    @SerializedName("fashion_feature")
    val fashionFeature: String?,
    @SerializedName("fashion_gender")
    val fashionGender: String?,
    @SerializedName("sales_rate")
    val salesRate: Int,
    @SerializedName("relative_sales_rate")
    val relativeSalesRate: Double,
    @SerializedName("image_url")
    val imageUrl: String,
    @SerializedName("image_url_handle")
    val imageUrlHandle: String,
    @SerializedName("image_url_resized")
    val imageUrlResized: ImageUrlResized,
    @SerializedName("_id")
    val internalId: String,
    @SerializedName("group_id")
    val groupId: String,
    @SerializedName("barcode")
    val barcode: String,
    @SerializedName("categories")
    val categories: List<ProductCategory>
)
