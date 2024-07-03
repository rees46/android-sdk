package com.personalizatio.api.entities.search

import com.google.gson.annotations.SerializedName

data class BrandEntity(
    @SerializedName("count")
    val count: Int,
    @SerializedName("name")
    val name: String
)
