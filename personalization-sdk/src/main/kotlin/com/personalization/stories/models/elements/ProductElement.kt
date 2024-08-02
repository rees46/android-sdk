package com.personalization.stories.models.elements

import com.personalization.Product
import org.json.JSONObject
import java.util.Objects

class ProductElement(json: JSONObject) : Element {
    val title: String = json.optString("title", "")
    var item: Product? = null

    init {
        val itemJson = json.optJSONObject("item")
        if (itemJson != null) {
            item = Product(itemJson)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProductElement) return false
        return title == other.title
                && item == other.item
    }

    override fun hashCode(): Int {
        return Objects.hash(title, item)
    }

    override fun toString(): String {
        return "ProductElement{title='$title', item=$item}"
    }
}
