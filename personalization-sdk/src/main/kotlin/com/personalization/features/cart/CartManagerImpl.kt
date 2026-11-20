package com.personalization.features.cart

import com.google.gson.Gson
import com.personalization.Params
import com.personalization.api.OnApiCallbackListener
import com.personalization.api.managers.CartManager
import com.personalization.api.params.CartParams
import com.personalization.api.responses.cart.CartContent
import com.personalization.api.responses.cart.CartContentResponse
import com.personalization.api.responses.cart.ClearCartResponse
import com.personalization.core.internal.isEmptyAnswer
import com.personalization.sdk.domain.usecases.network.SendNetworkMethodUseCase
import org.json.JSONObject
import javax.inject.Inject

private const val GET_CLIENT_SHOPPING_CART = "products/cart"
private const val CLEAR_CLIENT_SHOPPING_CART = "products/cart/clear"
private const val SHOP_SECRET = "shop_secret"

internal class CartManagerImpl @Inject constructor(
    private val sendNetworkMethodUseCase: SendNetworkMethodUseCase,
) : CartManager {

    override fun getClientShoppingCartContent(
        onGetCartContent: (CartContent) -> Unit,
        onError: (code: Int, msg: String?) -> Unit
    ) {
        sendNetworkMethodUseCase.getAsync(
            method = GET_CLIENT_SHOPPING_CART,
            params = Params().build(),
            listener = object : OnApiCallbackListener() {
                override fun onSuccess(response: JSONObject?) {
                    // A 2xx without a body is an empty cart: the callback hears it too.
                    val cartContent = if (response.isEmptyAnswer()) {
                        CartContent(content = emptyList())
                    } else {
                        Gson().fromJson(response.toString(), CartContentResponse::class.java).cartContent
                    }
                    onGetCartContent(cartContent)
                }

                override fun onError(code: Int, msg: String?) {
                    onError(code, msg)
                }
            }
        )
    }

    override fun clearClientShoppingCartContent(
        shopSecret: String,
        params: CartParams,
        onCartCleared: (Boolean) -> Unit,
        onError: (code: Int, msg: String?) -> Unit
    ) {
        sendNetworkMethodUseCase.getAsync(
            method = CLEAR_CLIENT_SHOPPING_CART,
            params = params.put(SHOP_SECRET, shopSecret).build(),
            listener = object : OnApiCallbackListener() {
                override fun onSuccess(response: JSONObject?) {
                    // A 2xx without a body: the server cleared the cart and said nothing more.
                    val cleared = response.isEmptyAnswer() ||
                        Gson().fromJson(response.toString(), ClearCartResponse::class.java).isSuccess()
                    onCartCleared(cleared)
                }

                override fun onError(code: Int, msg: String?) {
                    onError(code, msg)
                }
            })
    }
}
