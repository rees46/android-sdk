package com.personalization.api.responses.orders

import com.google.gson.annotations.SerializedName
import com.personalization.api.responses.product.Product

/**
 * A line item of a user [Order]. [item] is the catalog product (same shape as other product responses).
 *
 * [price] / [originalPrice] are returned by the API as strings.
 */
data class OrderItem(
    @SerializedName("amount")
    val amount: Int? = null,
    @SerializedName("price")
    val price: String? = null,
    @SerializedName("status")
    val status: String? = null,
    @SerializedName("original_price")
    val originalPrice: String? = null,
    @SerializedName("barcode")
    val barcode: String? = null,
    @SerializedName("line_id")
    val lineId: String? = null,
    @SerializedName("cancel_reason")
    val cancelReason: String? = null,
    @SerializedName("item")
    val item: Product? = null
)
