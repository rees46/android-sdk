package com.personalizatio.entities.recommended

import com.google.gson.annotations.SerializedName
import com.personalizatio.entities.products.Product

data class RecommendedFullEntity(
    @SerializedName("html")
    val html: String,
    @SerializedName("id")
    val id: Int,
    @SerializedName("recommends")
    val products: List<Product>,
    @SerializedName("title")
    val title: String
)
