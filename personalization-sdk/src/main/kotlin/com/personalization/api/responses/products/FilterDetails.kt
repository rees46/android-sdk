package com.personalization.api.responses.products

import com.google.gson.annotations.SerializedName

data class FilterDetails(
    @SerializedName("count")
    val count: Int,
    @SerializedName("priority")
    val priority: Int,
    @SerializedName("ranges")
    val ranges: List<Int>?,
    @SerializedName("values")
    val values: List<FilterValue>
)
