package com.personalization.api.responses.cart

import com.google.gson.annotations.SerializedName

data class CartContent(
    @SerializedName("items") val content: List<CartItem>
)
