package com.personalizatio.features.cart

import com.google.gson.Gson
import com.personalizatio.Params
import com.personalizatio.SDK
import com.personalizatio.api.OnApiCallbackListener
import com.personalizatio.api.entities.cart.CartEntity
import com.personalizatio.api.managers.CartManager
import com.personalizatio.api.params.ProductItemParams
import org.json.JSONObject

internal class CartManagerImpl(val sdk: SDK) : CartManager {

    override fun getCart(
        onGetCart: (CartEntity) -> Unit,
        onError: (Int, String?) -> Unit
    ) {
        getCart(object : OnApiCallbackListener() {
            override fun onSuccess(response: JSONObject?) {
                response?.let {
                    val cartEntity = Gson().fromJson(it.toString(), CartEntity::class.java)
                    onGetCart(cartEntity)
                }
            }

            override fun onError(code: Int, msg: String?) {
                onError(code, msg)
            }
        })
    }

    override fun getCart(
        listener: OnApiCallbackListener
    ) {
        sdk.getAsync(GET_CART_REQUEST, Params().build(), listener)
    }

    override fun addToCart(
        productId: String,
        amount: Int,
        listener: OnApiCallbackListener?
    ) {
        val params = createProductParams(productId, amount)
        addToCart(params, listener)
    }

    override fun addToCart(
        productId: String,
        amount: Int,
        price: Double,
        listener: OnApiCallbackListener?
    ) {
        val params = createProductParams(productId, amount, price = price)
        addToCart(params, listener)
    }

    override fun addToCart(
        productId: String,
        amount: Int,
        fashionSize: String,
        listener: OnApiCallbackListener?
    ) {
        val params = createProductParams(productId, amount, fashionSize = fashionSize)
        addToCart(params, listener)
    }

    override fun addToCart(
        productId: String,
        amount: Int,
        price: Double,
        fashionSize: String,
        listener: OnApiCallbackListener?
    ) {
        val params = createProductParams(productId, amount, price = price, fashionSize = fashionSize)
        addToCart(params, listener)
    }

    override fun addToCart(
        products: Map<String, Int>,
        listener: OnApiCallbackListener?
    ) {
        val params = createProductsParams(products)
        addToCart(params, listener)
    }

    override fun addToCart(
        params: Params,
        listener: OnApiCallbackListener?
    ) {
        sdk.track(Params.TrackEvent.CART, params, listener)
    }

    override fun removeFromCart(
        productId: String,
        amount: Int,
        listener: OnApiCallbackListener?
    ) {
        val params = createProductParams(productId, amount)
        removeFromCart(params, listener)
    }

    override fun removeFromCart(
        products: Map<String, Int>,
        listener: OnApiCallbackListener?
    ) {
        val params = createProductsParams(products)
        removeFromCart(params, listener)
    }

    override fun removeFromCart(
        params: Params,
        listener: OnApiCallbackListener?
    ) {
        sdk.track(Params.TrackEvent.REMOVE_FROM_CART, params, listener)
    }

    private fun createProductParams(productId: String, amount: Int, price: Double? = null, fashionSize: String? = null) : Params {
        return Params().put(createProductItemParams(productId, amount, price, fashionSize))
    }

    private fun createProductsParams(products: Map<String, Int>) : Params {
        val params = Params()
        for (product in products) {
            params.put(createProductItemParams(product.key, product.value))
        }
        return params
    }

    private fun createProductItemParams(productId: String, amount: Int, price: Double? = null, fashionSize: String? = null) : ProductItemParams {
        val productItemParams = ProductItemParams(productId)
            .set(ProductItemParams.PARAMETER.AMOUNT, amount)

        if(price != null) productItemParams.set(ProductItemParams.PARAMETER.PRICE, price)
        if(fashionSize != null) productItemParams.set(ProductItemParams.PARAMETER.FASHION_SIZE, fashionSize)

        return productItemParams
    }

    companion object {
        const val GET_CART_REQUEST = "products/cart"
    }
}
