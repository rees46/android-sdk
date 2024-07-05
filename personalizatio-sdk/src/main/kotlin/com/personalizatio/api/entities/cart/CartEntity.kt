package com.personalizatio.api.entities.cart

import com.google.gson.annotations.SerializedName

data class CartEntity(
    @SerializedName("data")
    val data: DataEntity,
    @SerializedName("status")
    val status: String
)
