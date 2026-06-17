package com.personalization.features.orders.impl

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.personalization.Params
import com.personalization.api.OnApiCallbackListener
import com.personalization.api.managers.OrdersManager
import com.personalization.api.responses.orders.GetLastOrderProductsResponse
import com.personalization.api.responses.orders.GetUserOrdersResponse
import com.personalization.api.responses.orders.Order
import com.personalization.api.responses.product.Product
import com.personalization.sdk.domain.usecases.network.SendNetworkMethodUseCase
import javax.inject.Inject
import org.json.JSONArray
import org.json.JSONObject

internal class OrdersManagerImpl @Inject constructor(
    private val sendNetworkMethodUseCase: SendNetworkMethodUseCase
) : OrdersManager {

    override fun getLastOrderProducts(
        params: Params,
        onGetLastOrderProducts: (GetLastOrderProductsResponse) -> Unit,
        onError: (Int, String?) -> Unit
    ) {
        sendNetworkMethodUseCase.getAsync(
            LAST_FOR_USER_REQUEST,
            params.build(),
            object : OnApiCallbackListener() {
                override fun onSuccess(response: JSONArray) {
                    val type = object : TypeToken<List<Product>>() {}.type
                    val products: List<Product> = Gson().fromJson(response.toString(), type)
                    onGetLastOrderProducts(GetLastOrderProductsResponse(products))
                }

                override fun onError(code: Int, msg: String?) {
                    onError(code, msg)
                }
            }
        )
    }

    override fun getUserOrders(
        shopSecret: String,
        did: String?,
        email: String?,
        phone: String?,
        loyaltyId: String?,
        externalId: String?,
        dateFrom: String?,
        onSuccess: (List<Order>) -> Unit,
        onError: (Int, String?) -> Unit
    ) {
        val params = Params()
        params.put(SHOP_SECRET_PARAM, shopSecret)
        did?.let { params.put(DID_PARAM, it) }
        email?.let { params.put(EMAIL_PARAM, it) }
        phone?.let { params.put(PHONE_PARAM, it) }
        loyaltyId?.let { params.put(LOYALTY_ID_PARAM, it) }
        externalId?.let { params.put(EXTERNAL_ID_PARAM, it) }
        dateFrom?.let { params.put(DATE_FROM_PARAM, it) }

        sendNetworkMethodUseCase.getAsync(
            BY_USER_REQUEST,
            params.build(),
            object : OnApiCallbackListener() {
                override fun onSuccess(response: JSONObject?) {
                    // Envelope: { "status": ..., "data": { "orders": [...] } }
                    val parsed = Gson().fromJson(
                        response.toString(),
                        GetUserOrdersResponse::class.java
                    )
                    onSuccess(parsed?.data?.orders ?: emptyList())
                }

                override fun onError(code: Int, msg: String?) {
                    onError(code, msg)
                }
            }
        )
    }

    companion object {
        const val LAST_FOR_USER_REQUEST = "orders/last_for_user"
        const val BY_USER_REQUEST = "orders/by_user"

        private const val SHOP_SECRET_PARAM = "shop_secret"
        private const val DID_PARAM = "did"
        private const val EMAIL_PARAM = "email"
        private const val PHONE_PARAM = "phone"
        private const val LOYALTY_ID_PARAM = "loyalty_id"
        private const val EXTERNAL_ID_PARAM = "external_id"
        private const val DATE_FROM_PARAM = "date_from"
    }
}
