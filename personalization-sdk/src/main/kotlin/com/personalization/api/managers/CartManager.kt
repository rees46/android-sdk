package com.personalization.api.managers

import com.personalization.api.params.CartParams
import com.personalization.api.responses.cart.CartContent
import org.json.JSONObject

interface CartManager {

    /**
     * Request client's shopping cart content
     *
     * @param onGetCartContent Callback for cart content
     * @param onError Callback for error
     */
    fun getClientShoppingCartContent(
        onGetCartContent: (CartContent) -> Unit,
        onError: (code: Int, msg: String?) -> Unit = { _: Int, _: String? -> }
    )

    /**
     * Clears the content of the client's shopping cart.
     *
     * @param shopSecret The secret key of the shop
     * @param params The parameters of the cart
     * @param onCartCleared Callback invoked upon successful cart clearing (passes a JSONObject or null)
     * @param onError Callback invoked in case of an error (error code and message)
     */
    fun clearClientShoppingCartContent(
        shopSecret: String,
        params: CartParams,
        onCartCleared: (JSONObject?) -> Unit,
        onError: (code: Int, msg: String?) -> Unit = { _: Int, _: String? -> }
    )
}
