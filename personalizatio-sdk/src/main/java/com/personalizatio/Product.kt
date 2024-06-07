package com.personalizatio

import org.json.JSONObject

class Product(json: JSONObject) {
    val id: String = json.optString("id", "")
    val name: String = json.optString("name", "")
    val brand: String = json.optString("brand", "")
    val image: String = json.optString("image_url", "")
    var oldPrice: String
    val price: String = json.optString("price_formatted", "")
    var discount: String
    val url: String = json.optString("url", "")
    val deeplink: String = json.optString("deeplink_android", "")
    val promocode: String = json.optString("promocode", "")
    val priceWithPromocode: String = json.optString("price_with_promocode_formatted", "")
    val discountPercent: String = json.optString("discount_percent", "")

    init {
        val oldPriceValue = json.optDouble("oldprice", 0.0)
        oldPrice = if (oldPriceValue > 0) {
            json.optString("oldprice_formatted", "")
        } else {
            ""
        }
        val priceValue = json.optDouble("price", 0.0)
        discount = if (oldPriceValue > 0 && oldPriceValue > priceValue) {
            json.optString("discount", "")
        } else {
            ""
        }
    }
}
