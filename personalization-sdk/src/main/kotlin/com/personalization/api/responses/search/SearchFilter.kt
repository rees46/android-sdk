package com.personalization.api.responses.search

import com.google.gson.annotations.SerializedName

/** One facet of a full search: how many products carry it and the count per value. */
data class SearchFilter(
    @SerializedName("count")
    val count: Int,
    @SerializedName("values")
    val values: Map<String, Int>
)
