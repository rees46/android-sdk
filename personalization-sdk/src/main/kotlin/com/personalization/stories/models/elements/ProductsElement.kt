package com.personalization.stories.models.elements

import com.personalization.Product
import java.util.Objects
import org.json.JSONObject

class ProductsElement(json: JSONObject) : Element {
    var labelHide: String = ""
    var labelShow: String = ""
    private val products: MutableList<Product> = ArrayList()

    init {
        val labelsJson = json.optJSONObject("labels")
        if (labelsJson != null) {
            labelHide = json.optString("hide_carousel", "")
            labelShow = json.optString("show_carousel", "")
        }
        val productsJsonArray = json.optJSONArray("products")
        if (productsJsonArray != null) {
            for (i in 0 until productsJsonArray.length()) {
                products.add(Product(productsJsonArray.optJSONObject(i)))
            }
        }
    }

    fun getProducts(): List<Product> {
        return products
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProductsElement) return false
        return labelHide == other.labelHide
            && labelShow == other.labelShow
            && products == other.products
    }

    override fun hashCode(): Int {
        return Objects.hash(labelHide, labelShow, products)
    }

    override fun toString(): String {
        return "ProductsElement{labelHide='$labelHide', labelShow='$labelShow', products=$products}"
    }
}
