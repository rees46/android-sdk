package com.personalizatio.products

import com.personalizatio.entities.products.cart.CartEntity
import com.personalizatio.entities.products.productInfo.ProductInfoEntity

abstract class OnProductsListener {
    open fun onGetProductInfo(productInfoEntity: ProductInfoEntity) {}

    open fun onGetCart(cartEntity: CartEntity) {}
}
