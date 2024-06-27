package com.personalizatio.products

import com.google.gson.Gson
import com.personalizatio.Params
import com.personalizatio.SDK
import com.personalizatio.api.OnApiCallbackListener
import com.personalizatio.entities.products.cart.CartEntity
import com.personalizatio.entities.products.productInfo.ProductInfoEntity
import org.json.JSONObject

internal class ProductsManager(val sdk: SDK) {

    internal fun getProductInfo(productId: String, listener: OnProductsListener) {
        getProductInfo(productId, object : OnApiCallbackListener() {
            override fun onSuccess(response: JSONObject?) {
                response?.let {
                    val productInfoEntity = Gson().fromJson(it.toString(), ProductInfoEntity::class.java)
                    listener.onGetProductInfo(productInfoEntity)
                }
            }
        })
    }

    internal fun getProductInfo(productId: String, listener: OnApiCallbackListener) {
        val params = Params().put(Params.Parameter.ITEM_ID, productId)
        sdk.getAsync(GET_PRODUCT_INFO_REQUEST, params.build(), listener)
    }

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

    companion object {
        const val GET_PRODUCT_INFO_REQUEST = "products/get/"
        const val GET_CART_REQUEST = "products/cart"
    }
}
