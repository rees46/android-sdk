package com.personalizatio.entities.recommendation

import com.google.gson.annotations.SerializedName
import com.personalizatio.entities.products.Product

data class ExtendedRecommendationEntity(
    @SerializedName("html")
    val html: String,
    @SerializedName("id")
    val id: Int,
    @SerializedName("recommends")
    val products: List<Product>,
    @SerializedName("title")
    val title: String
)
