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

private val DATE = Regex("""(\d{4})-(\d{2})-(\d{2})""")

/** The year of [value] when it is a day of the calendar as `YYYY-MM-DD` (`2026-02-30` is none); null otherwise. */
private fun calendarYear(value: String): Int? {
    val (year, month, day) = DATE.matchEntire(value)?.destructured?.toList()?.map { it.toInt() } ?: return null
    val days = when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
        else -> 0
    }
    return year.takeIf { day in 1..days }
}

/**
 * Whether [value] is an existing day of the calendar as `YYYY-MM-DD`: `2026-02-30` is none. A birthday
 * `loyalty/members/join` cannot read erases the date it has.
 */
internal fun isCalendarDate(value: String): Boolean = calendarYear(value) != null

/**
 * Whether [value] is a birthday `profile/set` takes: an existing day `YYYY-MM-DD` of the years 1000 to
 * 2999. The server ignores any other in silence.
 */
internal fun isBirthday(value: String): Boolean = calendarYear(value)?.let { it in 1000..2999 } == true

/** Fails with `invalidArgument` naming [name] unless [value] is a birthday `profile/set` takes ([isBirthday]). */
internal fun requireBirthday(value: String, name: String) {
    if (!isBirthday(value)) invalidArgument("$name must be a date YYYY-MM-DD of the years 1000 to 2999")
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

/**
 * The request of a server method — one that takes the shop's secret key: [shopSecret] under `shop_secret`.
 * Blank, it fails with `invalidArgument`; its value is never in a message. Sent with
 * `NamespaceCalls.request(secret = true)`, which keeps it out of the log and from the app's network logger.
 */
internal fun secretRequest(shopSecret: String): JSONObject =
    JSONObject().put(CoreHttpClient.SHOP_SECRET, requireNotBlank(shopSecret, "shopSecret"))

/**
 * Puts the customer's ids a server method was given — each a triple of the argument's name, the field and the
 * value — under their fields. An id given must not be blank, and one at least is needed: the request carries
 * no field of the device, so the server finds the customer by these alone.
 */
internal fun JSONObject.putCustomer(vararg ids: Triple<String, String, String?>): JSONObject {
    ids.forEach { (name, _, value) -> requireContact(value, name) }
    if (ids.all { it.third == null }) invalidArgument("one of ${ids.joinToString { it.first }} is needed")
    ids.forEach { (_, field, value) -> putIfNotNull(field, value) }
    return this
}
