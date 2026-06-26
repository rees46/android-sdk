package com.personalization.api.responses.products.counters

import com.google.gson.annotations.SerializedName

/**
 * Response for the `GET /products/counters` request.
 *
 * Envelope: `{ "daily": {...}, "now": {...}, "triggers": {...} }`.
 */
data class ProductCountersResponse(
    @SerializedName("daily")
    val daily: ProductCounter? = null,
    @SerializedName("now")
    val now: ProductCounter? = null,
    @SerializedName("triggers")
    val triggers: ProductCounterTriggers? = null
)

data class ProductCounter(
    @SerializedName("view")
    val view: Int = 0,
    @SerializedName("cart")
    val cart: Int = 0,
    @SerializedName("purchase")
    val purchase: Int = 0
)

data class ProductCounterTriggers(
    @SerializedName("back_in_stock")
    val backInStock: Int = 0,
    @SerializedName("price_drop")
    val priceDrop: Int = 0
)
