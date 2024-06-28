package com.personalizatio.api.managers

import com.personalizatio.api.OnApiCallbackListener
import com.personalizatio.api.listeners.OnCartListener

interface CartManager {

    /**
     * Request a cart
     *
     * @param listener Callback
     */
    fun getCart(listener: OnCartListener)

    /**
     * Request a cart
     *
     * @param listener Callback
     */
    fun getCart(listener: OnApiCallbackListener)

    /**
     * Remove product from cart
     *
     * @param productId Product ID
     * @param quantity Quantity for remove
     * @param listener Callback
     */
    fun removeFromCart(productId: String, quantity: Int, listener: OnApiCallbackListener? = null)

    /**
     * Remove products from cart
     *
     * @param products Product ID and Quantity pair map
     * @param listener Callback
     */
    fun removeFromCart(products: Map<String, Int>, listener: OnApiCallbackListener? = null)
}
