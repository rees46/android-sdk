package com.personalizatio

import org.json.JSONArray

class SearchParams : AbstractParams<SearchParams?>() {
    /**
     * https://reference.api.rees46.com/#full-search
     */
    enum class Parameter(protected var value: String?) : AbstractParams.ParamInterface {
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
        ;

        @Override
        fun getValue(): String? {
            return value
        }
    }


    /**
     * Типы поиска
     */
    enum class TYPE(protected var value: String?) {
        INSTANT("instant_search"),
        FULL("full_search");

        fun getValue(): String? {
            return value
        }
    }


    /**
     * Структура для фильтров
     */
    class SearchFilters {
        private val filters: HashMap<String?, Array<String?>?>? = HashMap()

        fun put(key: String?, values: Array<String?>?) {
            filters.put(key, values)
        }

        fun toString(): String? {
            val json: JSONObject = JSONObject()
            for (entry in filters.entrySet()) {
                val key: String = entry.getKey()
                try {
                    json.put(key, JSONArray(entry.getValue()))
                } catch (e: JSONException) {
                    SDK.warn(e.getMessage())
                }
            }
            return json.toString()
        }
    }

    fun put(param: Parameter?, value: SearchFilters?): SearchParams? {
        return put(param, value.toString())
    }
}
