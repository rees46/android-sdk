package com.personalization.api.responses.products.price

import com.google.gson.annotations.SerializedName

data class PriceRange(
    @SerializedName("min")
    val min: Double,
    @SerializedName("max")
    val max: Double
)
