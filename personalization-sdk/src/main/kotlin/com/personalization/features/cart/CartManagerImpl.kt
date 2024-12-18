package com.personalization.features.cart

import com.google.gson.Gson
import com.personalization.Params
import com.personalization.api.OnApiCallbackListener
import com.personalization.api.managers.CartManager
import com.personalization.api.responses.cart.CartContent
import com.personalization.api.responses.cart.CartContentResponse
import com.personalization.sdk.domain.usecases.network.SendNetworkMethodUseCase
import org.json.JSONObject
import javax.inject.Inject

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
                    response?.let {
                        val cartContentResponse = Gson().fromJson(
                            it.toString(), CartContentResponse::class.java
                        )
                        onGetCartContent(cartContentResponse.cartContent)
                    }
                }

                override fun onError(code: Int, msg: String?) {
                    onError(code, msg)
                }
            }
        )
    }

    companion object {
        const val GET_CLIENT_SHOPPING_CART = "products/cart"
    }
}
