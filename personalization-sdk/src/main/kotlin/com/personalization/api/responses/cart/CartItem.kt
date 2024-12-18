package com.personalization.api.responses.cart

import com.google.gson.annotations.SerializedName

data class CartItem(
    @SerializedName("uniqid") val id: String,
    @SerializedName("quantity") val quantity: Int
)
