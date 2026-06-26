package com.personalization.api.responses.orders

import com.google.gson.annotations.SerializedName

/**
 * A single user order returned by `orders/by_user`.
 *
 * Monetary fields (`value`, `cashValue`, ...) are returned by the API as strings.
 */
data class Order(
    @SerializedName("_id")
    val internalId: Long? = null,
    @SerializedName("id")
    val id: String? = null,
    @SerializedName("date")
    val date: String? = null,
    @SerializedName("value")
    val value: String? = null,
    @SerializedName("cash_value")
    val cashValue: String? = null,
    @SerializedName("bonuses_value")
    val bonusesValue: String? = null,
    @SerializedName("delivery_value")
    val deliveryValue: String? = null,
    @SerializedName("promocode")
    val promocode: String? = null,
    @SerializedName("delivery_date")
    val deliveryDate: String? = null,
    @SerializedName("internal_status")
    val internalStatus: String? = null,
    @SerializedName("stream")
    val stream: String? = null,
    @SerializedName("channel")
    val channel: String? = null,
    @SerializedName("tax_free")
    val taxFree: Boolean? = null,
    @SerializedName("delivery_type")
    val deliveryType: String? = null,
    @SerializedName("delivery_address")
    val deliveryAddress: String? = null,
    @SerializedName("order_status")
    val orderStatus: String? = null,
    @SerializedName("payment_type")
    val paymentType: String? = null,
    @SerializedName("items")
    val items: List<OrderItem> = emptyList()
)
