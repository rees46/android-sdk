package com.personalizatio.api.entities.categories.category

import com.google.gson.annotations.SerializedName

data class PriceRanges(
    @SerializedName("count")
    val count: Int,
    @SerializedName("from")
    val from: Double
)
