package com.personalization.api.managers

import com.personalization.Params
import com.personalization.api.responses.orders.GetLastOrderProductsResponse
import com.personalization.api.responses.orders.Order

interface OrdersManager {

    /**
     * Request the products of the user's last order
     *
     * @param params Parameters for the request
     * @param onGetLastOrderProducts Callback for get last order products
     * @param onError Callback for error
     */
    fun getLastOrderProducts(
        params: Params = Params(),
        onGetLastOrderProducts: (GetLastOrderProductsResponse) -> Unit,
        onError: (Int, String?) -> Unit = { _: Int, _: String? -> }
    )

    /**
     * Request the list of the user's orders (`orders/by_user`), ascending by date and internal id.
     *
     * The user is identified by [did] (the SDK's current device id is used when omitted) or by
     * [email] / [phone] / [loyaltyId] / [externalId]. [dateFrom] (YYYY-MM-DD) limits orders from a date.
     *
     * NOTE: [shopSecret] is a server-side secret key; shipping it inside a mobile app exposes it.
     * Use only in trusted contexts.
     *
     * @param shopSecret API secret key (required)
     * @param onSuccess Callback with the list of orders
     * @param onError Callback for error
     */
    fun getUserOrders(
        shopSecret: String,
        did: String? = null,
        email: String? = null,
        phone: String? = null,
        loyaltyId: String? = null,
        externalId: String? = null,
        dateFrom: String? = null,
        onSuccess: (List<Order>) -> Unit,
        onError: (Int, String?) -> Unit = { _: Int, _: String? -> }
    )
}
