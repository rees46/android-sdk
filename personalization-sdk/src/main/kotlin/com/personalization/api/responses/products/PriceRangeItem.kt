package com.personalization.api.responses.products

import com.google.gson.annotations.SerializedName

data class PriceRangeItem(
    @SerializedName("to")
    val to: Double,
    @SerializedName("count")
    val count: Int
)
