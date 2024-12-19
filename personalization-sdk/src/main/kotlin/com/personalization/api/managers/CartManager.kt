package com.personalization.api.managers

import com.personalization.api.responses.cart.CartContent

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
}
