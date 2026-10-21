package com.personalization.api.responses.product

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter

/**
 * Reads an optional integer the API does not always send as a number.
 *
 * `products/get` answers `"discount": false` for a product without a discount, while the
 * listings send the number of percent. A boolean (or anything else that is not a number)
 * becomes `null` instead of failing the whole response.
 */
internal class LenientIntAdapter : TypeAdapter<Int?>() {

    override fun write(out: JsonWriter, value: Int?) {
        if (value == null) out.nullValue() else out.value(value)
    }

    override fun read(reader: JsonReader): Int? = when (reader.peek()) {
        JsonToken.NUMBER -> reader.nextDouble().toInt()
        JsonToken.STRING -> reader.nextString().trim().toDoubleOrNull()?.toInt()
        JsonToken.NULL -> {
            reader.nextNull()
            null
        }
        else -> {
            reader.skipValue()
            null
        }
    }
}
