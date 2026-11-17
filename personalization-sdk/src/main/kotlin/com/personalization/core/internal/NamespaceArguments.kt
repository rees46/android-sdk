package com.personalization.core.internal

import com.personalization.core.SdkError
import com.personalization.core.SdkException
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.net.URLEncoder

/*
 * Checking a namespace method's arguments and writing them as the request's fields, by the contract's
 * rules (`contract/README.md`, "Параметры"; `contract/products.md`, `ProductsListRequest`), inside
 * [NamespaceCalls.checked]: a wrong argument is `invalidArgument` before any request, `null` is a field not
 * sent, a list goes as one string joined with commas and no spaces, an empty list is not sent, a fractional
 * number goes without trailing zeros (`100`, not `100.0`).
 */

/**
 * Ends the call with [SdkError.InvalidArgument]: [message] names the argument and the rule it breaks, never
 * its value. Inside [NamespaceCalls.checked], which logs it.
 */
internal fun invalidArgument(message: String): Nothing = throw SdkException(SdkError.InvalidArgument(message))

/** [value] when it is not blank; otherwise the call fails with `invalidArgument` naming [name]. */
internal fun requireNotBlank(value: String, name: String): String =
    value.takeIf { it.isNotBlank() } ?: invalidArgument("$name must not be empty")

/** Fails with `invalidArgument` when a contact or a customer's id is given and is empty: null is "not sent", not "". */
internal fun requireContact(value: String?, name: String) {
    if (value != null && value.isBlank()) invalidArgument("$name must not be empty")
}

/** Fails with `invalidArgument` when [value] is given and is below [min]. */
internal fun requireAtLeast(value: Int?, min: Int, name: String) {
    if (value != null && value < min) invalidArgument("$name must be at least $min")
}

/** Fails with `invalidArgument` when a price bound is negative or not a number, or when [min] is above [max]. */
internal fun requirePriceRange(min: Double?, max: Double?, minName: String = "priceMin", maxName: String = "priceMax") {
    if (min != null && !(min.isFinite() && min >= 0)) invalidArgument("$minName must be a number not below 0")
    if (max != null && !(max.isFinite() && max >= 0)) invalidArgument("$maxName must be a number not below 0")
    if (min != null && max != null && min > max) invalidArgument("$minName must not be above $maxName")
}

/** Puts [value] under [key] unless it is null. */
internal fun JSONObject.putIfNotNull(key: String, value: Any?): JSONObject {
    if (value != null) put(key, value)
    return this
}

/** Puts [values] under [key] as one string joined with commas and no spaces; nothing for null or an empty list. */
internal fun JSONObject.putList(key: String, values: List<String>?): JSONObject {
    if (!values.isNullOrEmpty()) put(key, values.joinToString(","))
    return this
}

/** Puts [value] under [key] as a number without trailing zeros (`100`, `99.5`); nothing for null. */
internal fun JSONObject.putNumber(key: String, value: Double?): JSONObject {
    if (value != null) put(key, plainNumber(value))
    return this
}

/**
 * Puts the free-form [filters] (a product's parameter → its values) under [key] as a JSON string:
 * `{"color":["red"]}`. Nothing for null or an empty map.
 */
internal fun JSONObject.putFilters(key: String, filters: Map<String, List<String>>?): JSONObject {
    if (filters.isNullOrEmpty()) return this
    val json = JSONObject()
    filters.forEach { (name, values) -> json.put(name, JSONArray(values)) }
    put(key, json.toString())
    return this
}

/** [value] as the API reads a number: without trailing zeros and without an exponent. */
internal fun plainNumber(value: Double): String = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

/** [value] encoded for one segment of a path: `a/b c` is `a%2Fb%20c`. */
internal fun pathSegment(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
