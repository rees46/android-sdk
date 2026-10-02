package com.personalization

import java.math.BigDecimal
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

    /**
     * Raw numeric prices, for comparing them (Stories V2 shows an old price only when it is greater
     * than the price). `null` when the field is absent or not a number. Read as decimals: a price may
     * have kopecks/cents, and an Int would silently drop them.
     */
    internal val priceDecimal: BigDecimal? = json.decimal("price")
    internal val oldPriceDecimal: BigDecimal? = json.decimal("oldprice")
    internal val priceWithPromocodeDecimal: BigDecimal? = json.decimal("price_with_promocode")

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

private fun JSONObject.decimal(name: String): BigDecimal? =
    opt(name)?.takeIf { it != JSONObject.NULL }?.toString()?.trim()?.toBigDecimalOrNull()
