package com.personalization.api.responses.products.filter

import com.google.gson.annotations.SerializedName

data class Filter(
    @SerializedName("filter")
    val filter: FilterDetails
)
