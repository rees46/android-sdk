package com.personalizatio.api.entities.search

import com.google.gson.annotations.SerializedName

data class PriceRangesEntity(
    @SerializedName("count")
    val count: Int,
    @SerializedName("from")
    val from: Double
)
