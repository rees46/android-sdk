package com.personalizatio.api.managers

import com.personalizatio.Params
import com.personalizatio.api.OnApiCallbackListener
import com.personalizatio.api.entities.cart.CartEntity

interface CartManager {

    /**
     * Request a cart
     *
     * @param onGetCart Callback for get cart
     * @param onError Callback for error
     */
    fun getCart(
        onGetCart: (CartEntity) -> Unit,
        onError: (Int, String?) -> Unit = { _: Int, _: String? -> }
    )

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
     * @param listener Callback
     */
    fun addToCart(
        productId: String,
        amount: Int,
        listener: OnApiCallbackListener? = null
    )

    /**
     * Add product to cart
     *
     * @param productId Product ID
     * @param amount Amount
     * @param price Price
     * @param listener Callback
     */
    fun addToCart(
        productId: String,
        amount: Int,
        price: Double,
        listener: OnApiCallbackListener? = null
    )

    /**
     * Add product to cart
     *
     * @param productId Product ID
     * @param amount Amount
     * @param fashionSize Fashion size
     * @param listener Callback
     */
    fun addToCart(
        productId: String,
        amount: Int,
        fashionSize: String,
        listener: OnApiCallbackListener? = null
    )

    /**
     * Add product to cart
     *
     * @param productId Product ID
     * @param amount Amount
     * @param price Price
     * @param fashionSize Fashion size
     * @param listener Callback
     */
    fun addToCart(
        productId: String,
        amount: Int,
        price: Double,
        fashionSize: String,
        listener: OnApiCallbackListener? = null
    )

    /**
     * Add products to cart
     *
     * @param products Product ID and Amount pair map
     * @param listener Callback
     */
    fun addToCart(
        products: Map<String, Int>,
        listener: OnApiCallbackListener? = null
    )

    /**
     * Add products to cart
     *
     * @param params Params
     * @param listener Callback
     */
    fun addToCart(
        params: Params,
        listener: OnApiCallbackListener? = null
    )

    /**
     * Remove product from cart
     *
     * @param productId Product ID
     * @param amount Amount
     * @param listener Callback
     */
    fun removeFromCart(
        productId: String,
        amount: Int,
        listener: OnApiCallbackListener? = null
    )

    /**
     * Remove products from cart
     *
     * @param products Product ID and Quantity pair map
     * @param listener Callback
     */
    fun removeFromCart(
        products: Map<String, Int>,
        listener: OnApiCallbackListener? = null
    )

    /**
     * Remove products from cart
     *
     * @param params Params
     * @param listener Callback
     */
    fun removeFromCart(
        params: Params,
        listener: OnApiCallbackListener? = null
    )
}
