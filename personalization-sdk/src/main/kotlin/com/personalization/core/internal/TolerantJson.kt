package com.personalization.core.internal

import org.json.JSONArray
import org.json.JSONObject

/*
 * Reading the API's answers into the namespaces' models, as the contract asks (`contract/README.md`,
 * "Ответы"): a field that is missing or `null` is null, a number may come as a string and a string as a
 * number, a value of the wrong kind is null rather than an error, and fields the model does not know are
 * never looked at. A model decides what it cannot do without; `decoding` is for an answer that does not
 * fit the model as a whole.
 */

/** The value of [key]; null when it is missing or JSON `null`. */
internal fun JSONObject.valueOrNull(key: String): Any? = opt(key)?.takeUnless { it == JSONObject.NULL }

/**
 * [key] as a string: a string as it is, a number as its digits (`37`, not `37.0`, for a whole one), a
 * boolean as `true` or `false`. Null when missing, `null`, an object or an array.
 */
internal fun JSONObject.stringOrNull(key: String): String? = when (val value = valueOrNull(key)) {
    is String -> value
    is Number -> value.wholeOrNull()?.toString() ?: value.toString()
    is Boolean -> value.toString()
    else -> null
}

/** [stringOrNull] that is null for an empty or blank string too: for ids and codes. */
internal fun JSONObject.nonBlankStringOrNull(key: String): String? = stringOrNull(key)?.takeIf { it.isNotBlank() }

/** [key] as a whole number that fits an [Int], given as a number or a string (`2`, `"2"`, `2.0`); otherwise null. */
internal fun JSONObject.intOrNull(key: String): Int? =
    longOrNull(key)?.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()

/** [key] as a whole number, given as a number or a string (`2`, `"2"`, `2.0`); otherwise null. */
internal fun JSONObject.longOrNull(key: String): Long? = when (val value = valueOrNull(key)) {
    is Number -> value.wholeOrNull()
    is String -> value.trim().let { text -> text.toLongOrNull() ?: text.toDoubleOrNull()?.wholeOrNull() }
    else -> null
}

/** [key] as a finite number, given as a number or a string (`999.5`, `"999.5"`); otherwise null. */
internal fun JSONObject.doubleOrNull(key: String): Double? = when (val value = valueOrNull(key)) {
    is Number -> value.toDouble()
    is String -> value.trim().toDoubleOrNull()
    else -> null
}?.takeIf { it.isFinite() }

/** [key] as a boolean: `true`/`false`, as JSON or as a string, or `1`/`0`; otherwise null. */
internal fun JSONObject.booleanOrNull(key: String): Boolean? = when (val value = valueOrNull(key)) {
    is Boolean -> value
    is Number -> when (value.wholeOrNull()) {
        1L -> true
        0L -> false
        else -> null
    }
    is String -> when (value.trim().lowercase()) {
        "true", "1" -> true
        "false", "0" -> false
        else -> null
    }
    else -> null
}

/** [key] as an object; null when it is missing or of another kind. */
internal fun JSONObject.objectOrNull(key: String): JSONObject? = valueOrNull(key) as? JSONObject

/** [key] as an array; null when it is missing or of another kind. */
internal fun JSONObject.arrayOrNull(key: String): JSONArray? = valueOrNull(key) as? JSONArray

/**
 * The objects of the array read with [read], in order. An element that is not an object, or that [read]
 * turns down with null, is left out: one bad element does not lose the others.
 */
internal fun <T : Any> JSONArray.mapObjects(read: (JSONObject) -> T?): List<T> =
    (0 until length()).mapNotNull { index -> (opt(index) as? JSONObject)?.let(read) }

/** The strings of the array, numbers written as [stringOrNull] writes them; anything else is left out. */
internal fun JSONArray.strings(): List<String> = (0 until length()).mapNotNull { index ->
    when (val value = opt(index)) {
        is String -> value
        is Number -> value.wholeOrNull()?.toString() ?: value.toString()
        else -> null
    }
}

/**
 * The value of a closed set whose wire name is [raw]; null for a name the SDK does not know, so a value the
 * server adds later leaves the field empty instead of failing the answer.
 */
internal fun <T> valueOfWire(raw: String?, values: Iterable<T>, wire: (T) -> String): T? =
    raw?.let { name -> values.firstOrNull { wire(it) == name } }

/** The number as a [Long] when it is whole and fits one; otherwise null. */
private fun Number.wholeOrNull(): Long? = when (this) {
    is Int -> toLong()
    is Long -> this
    is Short -> toLong()
    is Byte -> toLong()
    else -> toDouble().takeIf { it.isFinite() && it == Math.floor(it) && it >= Long.MIN_VALUE && it <= Long.MAX_VALUE }?.toLong()
}
