package com.personalizatio.api.entities.search

import com.google.gson.annotations.SerializedName

data class PriceRangeEntity(
    @SerializedName("max")
    val max: Double,
    @SerializedName("min")
    val min: Double
)
