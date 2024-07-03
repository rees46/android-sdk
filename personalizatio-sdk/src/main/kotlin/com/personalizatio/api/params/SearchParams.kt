package com.personalizatio.api.params

import com.personalizatio.AbstractParams
import com.personalizatio.SDK
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class SearchParams : AbstractParams<SearchParams>() {

    enum class Parameter(override var value: String) : ParamInterface {
        PAGE("page"),
        LIMIT("limit"),
        CATEGORY_LIMIT("category_limit"),
        CATEGORIES("categories"),
        EXTENDED("extended"),
        SORT_BY("sort_by"),
        SORT_DIR("sort_dir"),
        LOCATIONS("locations"),
        BRANDS("brands"),
        FILTERS("filters"),
        PRICE_MIN("price_min"),
        PRICE_MAX("price_max"),
        COLORS("colors"),
        FASHION_SIZES("fashion_sizes"),
        EXCLUDE("exclude"),
        NO_CLARIFICATION("no_clarification")
    }

    enum class TYPE(var value: String) {
        INSTANT("instant_search"),
        FULL("full_search")
    }

    class SearchFilters {
        private val filters = HashMap<String, Array<String>>()

        fun put(key: String, values: Array<String>) {
            filters[key] = values
        }

        override fun toString(): String {
            val json = JSONObject()
            for ((key, value) in filters) {
                try {
                    json.put(key, JSONArray(value))
                } catch (e: JSONException) {
                    SDK.warn(e.message)
                }
            }
            return json.toString()
        }
    }

    fun put(param: Parameter, value: SearchFilters): SearchParams {
        return put(param, value.toString())
    }
}