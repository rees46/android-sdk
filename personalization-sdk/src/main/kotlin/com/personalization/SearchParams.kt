package com.personalization

import com.personalization.logs.SdkLogs
import com.personalization.logs.LogCodes
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

@Deprecated(
    "This class will be removed in future versions.",
    level = DeprecationLevel.WARNING, replaceWith = ReplaceWith(
        "SearchParams",
        "com.personalization.api.params.SearchParams"
    )
)
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

        //params.put(SearchParams.Parameter.NO_CLARIFICATION, true);
        NO_CLARIFICATION("no_clarification"),
    }


    /**
     * Типы поиска
     */
    enum class TYPE(@JvmField var value: String) {
        INSTANT("instant_search"),
        FULL("full_search")
    }


    /**
     * Структура для фильтров
     */
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
                    SDK.warn("SearchParams: a filter is left out: ${e.javaClass.simpleName}")
                }
            }
            return json.toString()
        }
    }

    fun put(param: Parameter?, value: SearchFilters): SearchParams {
        if (param == null) {
            SdkLogs.log(LogLevel.WARNING, LogCodes.INVALID_ARGUMENT, "SearchParams.put: no parameter given, the filters are left out")
            return this
        }
        return put(param, value.toString())
    }
}
