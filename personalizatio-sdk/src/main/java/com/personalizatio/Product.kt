package com.personalizatio

import org.json.JSONException

class Product(product: JSONObject?) {
    val id: String? = product.getString("id")
    val name: String? = product.getString("name")
    val brand: String? = null
    val image: String? = product.getString("image_url")
    val oldprice: String? = null
    val price: String?
    val discount: String? = null
    val url: String?
    val deeplink: String? = null
    val promocode: String? = null
    val price_with_promocode: String? = null
    val discount_percent: String? = null

    init {
        oldprice = if (product.has("oldprice") && product.getDouble("oldprice") > 0) {
            product.getString("oldprice_formatted")
        } else {
            null
        }
        brand = if (product.has("brand")) {
            product.getString("brand")
        } else {
            null
        }
        promocode = if (product.has("promocode")) {
            product.getString("promocode")
        } else {
            null
        }
        price_with_promocode = if (product.has("price_with_promocode")) {
            product.getString("price_with_promocode_formatted")
        } else {
            null
        }
        discount_percent = if (product.has("discount_percent")) {
            product.getString("discount_percent")
        } else {
            null
        }
        price = product.getString("price_formatted")
        url = product.getString("url")
        deeplink = if (product.has("deeplink_android")) {
            product.getString("deeplink_android")
        } else {
            null
        }
        discount =
            if (product.has("discount") && product.getDouble("oldprice") > 0 && product.getDouble("oldprice") > product.getDouble(
                    "price"
                )
            ) {
                product.getString("discount")
            } else {
                null
            }
    }
}
