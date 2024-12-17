package com.personalization.api.managers

import com.personalization.api.OnApiCallbackListener

interface CartManager {

    fun getClientShoppingCart(
        listener: OnApiCallbackListener?
    )
}
