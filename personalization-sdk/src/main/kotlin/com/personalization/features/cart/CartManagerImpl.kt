package com.personalization.features.cart

import com.personalization.Params
import com.personalization.api.OnApiCallbackListener
import com.personalization.api.managers.CartManager
import com.personalization.sdk.domain.usecases.network.SendNetworkMethodUseCase
import javax.inject.Inject

internal class CartManagerImpl @Inject constructor(
    private val sendNetworkMethodUseCase: SendNetworkMethodUseCase,
) : CartManager {

    override fun getClientShoppingCart(listener: OnApiCallbackListener?) {
        sendNetworkMethodUseCase.getAsync(
            method = GET_CLIENT_SHOPPING_CART,
            params = Params().build(),
            listener = listener
        )
    }

    companion object {
        const val GET_CLIENT_SHOPPING_CART = "products/cart"
    }
}
