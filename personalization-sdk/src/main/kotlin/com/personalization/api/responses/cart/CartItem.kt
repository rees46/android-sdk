package com.personalization.api.responses.cart

import com.google.gson.annotations.SerializedName

/** One product in the customer's cart: its id ([id], `uniqid` in the answer) and how many of it. */
data class CartItem(
    @SerializedName("uniqid") val id: String,
    @SerializedName("quantity") val quantity: Int
)
