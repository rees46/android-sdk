package com.personalization.api.responses.cart

import com.google.gson.annotations.SerializedName

data class CartContentResponse(
    @SerializedName("status") val status: String,
    @SerializedName("data") val cartContent: CartContent
)
