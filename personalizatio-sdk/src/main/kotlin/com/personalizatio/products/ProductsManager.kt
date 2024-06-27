package com.personalizatio.products

import com.google.gson.Gson
import com.personalizatio.Params
import com.personalizatio.SDK
import com.personalizatio.api.OnApiCallbackListener
import com.personalizatio.entities.products.productInfo.ProductInfoEntity
import org.json.JSONObject

internal class ProductsManager(val sdk: SDK) {

    private val cartManager: CartManager by lazy {
        CartManager(sdk)
    }

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
        cartManager.getCart(listener)
    }

    internal fun getCart(listener: OnApiCallbackListener) {
        cartManager.getCart(listener)
    }

    internal fun removeFromCart(productId: String, quantity: Int, listener: OnApiCallbackListener? = null) {
        cartManager.removeFromCart(productId, quantity, listener)
    }

    internal fun removeFromCart(products: Map<String, Int>, listener: OnApiCallbackListener? = null) {
        cartManager.removeFromCart(products, listener)
    }

    companion object {
        const val GET_PRODUCT_INFO_REQUEST = "products/get/"
    }
}
