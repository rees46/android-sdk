package com.personalization.api.responses.product

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter

/**
 * Reads an optional number the API does not always send as a number.
 *
 * `products/get` sends numbers as strings, and an empty string where there is no value
 * (`"oldprice": ""`). A number or a numeric string is read; an empty string, a boolean or
 * anything else becomes `null` instead of failing the whole response. Written back as a
 * plain number, so the model survives a round trip through Gson.
 */
internal class LenientDoubleAdapter : TypeAdapter<Double?>() {

    override fun write(out: JsonWriter, value: Double?) {
        if (value == null) out.nullValue() else out.value(value)
    }

    override fun read(reader: JsonReader): Double? = when (reader.peek()) {
        JsonToken.NUMBER -> reader.nextDouble()
        JsonToken.STRING -> reader.nextString().trim().toDoubleOrNull()?.takeIf { it.isFinite() }
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
