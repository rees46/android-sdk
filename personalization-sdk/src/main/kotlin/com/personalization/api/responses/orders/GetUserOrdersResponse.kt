package com.personalization.api.responses.orders

import com.google.gson.annotations.SerializedName

/**
 * Envelope of the `orders/by_user` response: `{ "status": ..., "data": { "orders": [...] } }`.
 */
internal data class GetUserOrdersResponse(
    @SerializedName("status")
    val status: String? = null,
    @SerializedName("data")
    val data: UserOrdersData? = null
)

internal data class UserOrdersData(
    @SerializedName("orders")
    val orders: List<Order> = emptyList()
)
