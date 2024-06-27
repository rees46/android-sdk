package com.personalizatio.products

import com.google.gson.Gson
import com.personalizatio.Params
import com.personalizatio.SDK
import com.personalizatio.api.OnApiCallbackListener
import com.personalizatio.entities.products.cart.CartEntity
import org.json.JSONObject

internal class CartManager(val sdk: SDK) {

    internal fun getCart(listener: OnProductsListener) {
        getCart(object : OnApiCallbackListener() {
            override fun onSuccess(response: JSONObject?) {
                response?.let {
                    val cartEntity = Gson().fromJson(it.toString(), CartEntity::class.java)
                    listener.onGetCart(cartEntity)
                }
            }
        })
    }

    internal fun getCart(listener: OnApiCallbackListener) {
        sdk.getAsync(GET_CART_REQUEST, Params().build(), listener)
    }

    internal fun removeFromCart(productId: String, quantity: Int, listener: OnApiCallbackListener? = null) {
        val params = Params().put(createProductItem(productId, quantity))
        sdk.track(Params.TrackEvent.REMOVE_FROM_CART, params, listener)
    }

    internal fun removeFromCart(products: Map<String, Int>, listener: OnApiCallbackListener? = null) {
        val params = Params()
        for (product in products) {
            params.put(createProductItem(product.key, product.value))
        }
        sdk.track(Params.TrackEvent.REMOVE_FROM_CART, params, listener)
    }

    private fun createProductItem(productId: String, quantity: Int) : Params.Item {
        return Params.Item(productId).set(Params.Item.COLUMN.AMOUNT, quantity)
    }

    companion object {
        const val GET_CART_REQUEST = "products/cart"
    }
}
