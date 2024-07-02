package com.personalizatio.entities.recommendation

import com.google.gson.annotations.SerializedName

data class RecommendationEntity(
    @SerializedName("html")
    val html: String,
    @SerializedName("id")
    val id: Int,
    @SerializedName("recommends")
    val productIds: List<Int>,
    @SerializedName("title")
    val title: String
)
