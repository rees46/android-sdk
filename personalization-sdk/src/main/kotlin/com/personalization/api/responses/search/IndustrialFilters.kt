package com.personalization.api.responses.search

import com.google.gson.annotations.SerializedName

/** Fashion facets of a full search: colours and sizes with product counts. */
data class IndustrialFilters(
    @SerializedName("colors")
    val colors: List<ColorFilter>? = null,
    @SerializedName("fashion_sizes")
    val fashionSizes: List<SizeFilter>? = null
)

data class ColorFilter(
    @SerializedName("color")
    val color: String,
    @SerializedName("count")
    val count: Int
)

data class SizeFilter(
    @SerializedName("size")
    val size: String,
    @SerializedName("count")
    val count: Int
)
