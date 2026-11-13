package com.personalization.api.params

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

//TODO use data loader
class ProfileParams private constructor(
    private val params: JSONObject = JSONObject()
) {

    class Builder {

        private val params: JSONObject = JSONObject()

        fun put(param: String, value: String): Builder {
            params.put(param, value)
            return this
        }

        fun put(param: String, value: Int): Builder {
            params.put(param, value)
            return this
        }

        fun put(param: String, value: Float): Builder {
            params.put(param, value)
            return this
        }

        /**
         * A date goes as `YYYY-MM-DD` in the device's time zone: the server reads `birthday` so. Build
         * it at local midnight (`Calendar.getInstance()`); a date picker that returns UTC midnight
         * gives the day before on devices west of UTC — pass such a date as a `String` instead.
         */
        fun put(param: String, value: Date): Builder {
            params.put(param, formatDate(value))
            return this
        }

        fun put(param: String, value: Boolean): Builder {
            params.put(param, value)
            return this
        }

        fun put(param: String, value: JSONObject): Builder {
            params.put(param, value)
            return this
        }

        fun put(param: String, value: Array<String>): Builder {
            params.put(param, JSONArray().apply { value.forEach { put(it) } })
            return this
        }

        fun put(param: String, value: Array<Int>): Builder {
            params.put(param, JSONArray().apply { value.forEach { put(it) } })
            return this
        }

        /** `0.1f` goes as `0.1`, not `0.10000000149011612`; NaN and infinity are not JSON and are left out. */
        fun put(param: String, value: Array<Float>): Builder {
            params.put(param, JSONArray().apply { value.filter { it.isFinite() }.forEach { put(it.toString().toDouble()) } })
            return this
        }

        fun put(param: String, value: Array<Date>): Builder {
            params.put(param, JSONArray().apply { value.forEach { put(formatDate(it)) } })
            return this
        }

        fun put(param: String, value: Array<Boolean>): Builder {
            params.put(param, JSONArray().apply { value.forEach { put(it) } })
            return this
        }

        fun put(param: String, value: Array<JSONObject>): Builder {
            params.put(param, JSONArray().apply { value.forEach { put(it) } })
            return this
        }

        fun build(): ProfileParams {
            return ProfileParams(params)
        }

        // A raw `Date` or Java array in org.json is written with its `toString()`: the server got
        // "Thu Nov 13 10:00:00 GMT+03:00 2026" for a birthday and "[Ljava.lang.String;@1a2b3c" for a
        // list, and answered 200 to both.
        private fun formatDate(value: Date): String =
            SimpleDateFormat(DATE_FORMAT, Locale.US).format(value)
    }

    private companion object {
        const val DATE_FORMAT = "yyyy-MM-dd"
    }

    fun toJson(): JSONObject = params
}
