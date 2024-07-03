package com.personalizatio.api.entities.recommendation

import com.google.gson.annotations.SerializedName
import com.personalizatio.api.entities.product.ProductEntity

data class ExtendedRecommendationEntity(
    @SerializedName("html")
    val html: String,
    @SerializedName("id")
    val id: Int,
    @SerializedName("recommends")
    val products: List<ProductEntity>,
    @SerializedName("title")
    val title: String
)
