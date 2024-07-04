package com.personalizatio.api.entities.product

import com.google.gson.annotations.SerializedName

data class BrandEntity(
    @SerializedName("count")
    val count: Int,
    @SerializedName("name")
    val name: String
)
