package com.personalization.api.responses.products.filter

import com.google.gson.annotations.SerializedName

data class FilterValue(
    @SerializedName("value")
    val value: String,
    @SerializedName("count")
    val count: Int
)
