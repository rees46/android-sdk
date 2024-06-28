package com.personalizatio.cart

import com.google.gson.Gson
import com.personalizatio.Params
import com.personalizatio.SDK
import com.personalizatio.api.OnApiCallbackListener
import com.personalizatio.api.listeners.OnCartListener
import com.personalizatio.api.managers.CartManager
import com.personalizatio.entities.products.cart.CartEntity
import org.json.JSONObject

internal class CartManagerImpl(val sdk: SDK) : CartManager {

    override fun getCart(listener: OnCartListener) {
        getCart(object : OnApiCallbackListener() {
            override fun onSuccess(response: JSONObject?) {
                response?.let {
                    val cartEntity = Gson().fromJson(it.toString(), CartEntity::class.java)
                    listener.onGetCart(cartEntity)
                }
            }
        })
    }

    override fun getCart(listener: OnApiCallbackListener) {
        sdk.getAsync(GET_CART_REQUEST, Params().build(), listener)
    }

    override fun removeFromCart(productId: String, quantity: Int, listener: OnApiCallbackListener?) {
        val params = Params().put(createProductItem(productId, quantity))
        sdk.track(Params.TrackEvent.REMOVE_FROM_CART, params, listener)
    }

    override fun removeFromCart(products: Map<String, Int>, listener: OnApiCallbackListener?) {
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
