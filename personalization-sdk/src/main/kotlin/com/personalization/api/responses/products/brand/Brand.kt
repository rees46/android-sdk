package com.personalization.api.responses.products.brand

import com.google.gson.annotations.SerializedName

data class Brand(
    @SerializedName("name")
    val name: String,
    @SerializedName("picture")
    val picture: String,
    @SerializedName("count")
    val count: Int
)
