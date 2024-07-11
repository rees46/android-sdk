package com.personalizatio.api.responses.search

import com.google.gson.annotations.SerializedName

data class Brand(
    @SerializedName("count")
    val count: Int,
    @SerializedName("name")
    val name: String
)
