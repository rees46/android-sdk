package com.personalizatio.api.managers

import com.personalizatio.Params
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
     * Add product to cart
     *
     * @param productId Product ID
     * @param amount Amount
     */
    fun addToCart(productId: String, amount: Int)

    /**
     * Add product to cart
     *
     * @param productId Product ID
     * @param amount Amount
     * @param price Price
     */
    fun addToCart(productId: String, amount: Int, price: Double)

    /**
     * Add product to cart
     *
     * @param productId Product ID
     * @param amount Amount
     * @param fashionSize Fashion size
     */
    fun addToCart(productId: String, amount: Int, fashionSize: String)

    /**
     * Add product to cart
     *
     * @param productId Product ID
     * @param amount Amount
     * @param price Price
     * @param fashionSize Fashion size
     */
    fun addToCart(productId: String, amount: Int, price: Double, fashionSize: String)

    /**
     * Add products to cart
     *
     * @param products Product ID and Amount pair map
     */
    fun addToCart(products: Map<String, Int>)

    /**
     * Add products to cart
     *
     * @param params Params
     */
    fun addToCart(params: Params)

    /**
     * Remove product from cart
     *
     * @param productId Product ID
     * @param amount Amount
     * @param listener Callback
     */
    fun removeFromCart(productId: String, amount: Int, listener: OnApiCallbackListener? = null)

    /**
     * Remove products from cart
     *
     * @param products Product ID and Quantity pair map
     * @param listener Callback
     */
    fun removeFromCart(products: Map<String, Int>, listener: OnApiCallbackListener? = null)

    /**
     * Remove products from cart
     *
     * @param params Params
     * @param listener Callback
     */
    fun removeFromCart(params: Params, listener: OnApiCallbackListener? = null)
}
