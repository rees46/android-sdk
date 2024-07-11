package com.personalizatio.api.responses.search

import com.google.gson.annotations.SerializedName

data class PriceRanges(
    @SerializedName("count")
    val count: Int,
    @SerializedName("from")
    val from: Double
)
