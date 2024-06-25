package com.personalizatio.products

import com.personalizatio.entities.products.productInfo.ProductInfoEntity

interface OnProductsListener {
    fun onGetProductInfo(productInfoEntity: ProductInfoEntity) {}
}
