package com.personalization.core.internal

import org.json.JSONObject
import java.math.BigDecimal

/**
 * The new core sends an event's numbers as JSON numbers, the way the contract's fixtures have them:
 * the older builders put a cart line's `amount` and `price`, an order's price and a popup's id in as
 * text, which the server reads either way. Only text that is a whole plain number is turned into one:
 * `"0123"`, `"1e3"`, `" 2"` or `"2 pcs"` stay as they are. The legacy core sends what it always has.
 */
internal object EventNumbers {

    /** Turns the numbers of [body], an event of [path], into JSON numbers; returns [body]. */
    fun normalize(path: String, body: JSONObject): JSONObject {
        when (path) {
            EventQueue.PATH_PUSH -> {
                decimal(body, ORDER_PRICE)
                val items = body.optJSONArray(ITEMS) ?: return body
                for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index) ?: continue
                    integer(item, AMOUNT)
                    decimal(item, PRICE)
                }
            }
            EventQueue.PATH_CUSTOM -> {
                integer(body, TIME)
                integer(body, VALUE)
            }
            PATH_POPUP_SHOWED -> integer(body, POPUP)
        }
        return body
    }

    private fun integer(json: JSONObject, key: String) {
        val text = json.opt(key) as? String ?: return
        val number = text.toLongOrNull()?.takeIf { it.toString() == text } ?: return
        json.put(key, number)
    }

    private fun decimal(json: JSONObject, key: String) {
        val text = json.opt(key) as? String ?: return
        if (!PLAIN_DECIMAL.matches(text)) return
        // Kept as written: a BigDecimal goes out as its own digits, never in scientific notation.
        json.put(key, BigDecimal(text))
    }

    private val PLAIN_DECIMAL = Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?")

    private const val PATH_POPUP_SHOWED = "popup/showed"
    private const val ORDER_PRICE = "order_price"
    private const val ITEMS = "items"
    private const val AMOUNT = "amount"
    private const val PRICE = "price"
    private const val TIME = "time"
    private const val VALUE = "value"
    private const val POPUP = "popup"
}
