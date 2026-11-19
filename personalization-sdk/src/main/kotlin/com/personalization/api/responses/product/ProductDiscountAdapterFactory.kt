package com.personalization.api.responses.product

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter

/**
 * Reads a [Product] as it always was read, with one addition: an answer without `discount_percent` — a line
 * of an order among them — gives [Product.discountPercent] from `discount` when that is a number (contract
 * `products`). `discount` as a boolean (`products/get`) leaves it null, as before.
 */
internal class ProductDiscountAdapterFactory : TypeAdapterFactory {

    override fun <T> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T>? {
        if (type.rawType != Product::class.java) return null
        val delegate = gson.getDelegateAdapter(this, type)
        val tree = gson.getAdapter(JsonElement::class.java)
        return object : TypeAdapter<T>() {
            override fun write(out: JsonWriter, value: T) = delegate.write(out, value)

            override fun read(reader: JsonReader): T? {
                val json = tree.read(reader)
                if (json != null && json.isJsonObject) {
                    val product = json.asJsonObject
                    val percent = product.get(DISCOUNT_PERCENT)
                    val discount = product.get(DISCOUNT)
                    if ((percent == null || percent.isJsonNull) && discount is JsonPrimitive && !discount.isBoolean) {
                        product.add(DISCOUNT_PERCENT, discount)
                    }
                }
                return delegate.fromJsonTree(json)
            }
        }
    }

    private companion object {
        const val DISCOUNT_PERCENT = "discount_percent"
        const val DISCOUNT = "discount"
    }
}
