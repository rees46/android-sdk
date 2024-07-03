package com.personalizatio.api.entities.cart

import com.google.gson.annotations.SerializedName

data class ItemEntity(
    @SerializedName("quantity")
    val quantity: Int,
    @SerializedName("uniqid")
    val uniqid: String
)
