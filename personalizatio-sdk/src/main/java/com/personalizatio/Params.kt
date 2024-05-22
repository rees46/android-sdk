package com.personalizatio

import android.util.Log

class Params : AbstractParams<Params?>() {
    /**
     * Основные параметры
     */
    enum class Parameter(protected var value: String?) : ParamInterface {
        LIMIT("limit"),
        ITEM("item"),
        PRICE("price"),
        LOCATIONS("locations"),

        /**
         * Available sizes: 120, 140, 160, 180, 200, 220, 310, 520
         */
        IMAGE_SIZE("resize_image"),
        BRANDS("brands"),
        EXCLUDE_BRANDS("exclude_brands"),
        CATEGORIES("categories"),
        DISCOUNT("discount"),
        FULL_CART("full_cart"),
        ORDER_ID("order_id"),
        ORDER_PRICE("order_price"),
        DELIVERY_ADDRESS("delivery_address"),
        DELIVERY_TYPE("delivery_type"),
        PROMOCODE("promocode"),
        PAYMENT_TYPE("payment_type"),
        TAX_FREE("tax_free"),
        CATEGORY_ID("category_id"),
        CATEGORY("category"),
        SEARCH_QUERY("search_query"),
        EXTENDED("extended"),
        ;

        @Override
        fun getValue(): String? {
            return value
        }
    }

    /**
     * Типы рекомендаций
     */
    class RecommendedBy {
        enum class TYPE(protected var value: String?) {
            RECOMMENDATION("dynamic"),
            TRIGGER("chain"),
            BULK("bulk"),
            TRANSACTIONAL("transactional"),
            INSTANT_SEARCH("instant_search"),
            FULL_SEARCH("full_search"),
            STORIES("stories"),
            ;

            fun getValue(): String? {
                return value
            }
        }

        var type: String?
        var code: String? = null

        constructor(type: TYPE?) {
            this.type = type.getValue()
        }

        constructor(type: TYPE?, code: String?) {
            this.type = type.getValue()
            this.code = code
        }
    }

    /**
     * Товар
     */
    class Item(@NonNull id: String?) {
        enum class COLUMN(var value: String?) {
            ID("id"),
            AMOUNT("amount"),
            PRICE("price"),
            FASHION_SIZE("fashion_size"),
        }

        val columns: HashMap<String?, String?>? = HashMap()

        init {
            columns.put(COLUMN.ID.value, id)
        }

        fun set(column: COLUMN?, @NonNull value: String?): Item? {
            columns.put(column.value, value)
            return this
        }

        fun set(column: COLUMN?, value: Int): Item? {
            return set(column, String.valueOf(value))
        }

        fun set(column: COLUMN?, value: Double): Item? {
            return set(column, String.valueOf(value))
        }

        fun set(column: COLUMN?, value: Boolean): Item? {
            return set(column, if (value) "1" else "0")
        }
    }

    enum class TrackEvent(var value: String?) {
        VIEW("view"),
        CATEGORY("category"),
        CART("cart"),
        REMOVE_FROM_CART("remove_from_cart"),
        PURCHASE("purchase"),
        SEARCH("search"),
        WISH("wish"),
        REMOVE_FROM_WISH("remove_wish"),
    }

    /**
     * Вставка параметров рекомендаций
     */
    fun put(recommended_by: RecommendedBy?): Params? {
        try {
            params.put(InternalParameter.RECOMMENDED_BY.getValue(), recommended_by.type)
            if (recommended_by.code != null) {
                params.put(InternalParameter.RECOMMENDED_CODE.getValue(), recommended_by.code)
            }
        } catch (e: JSONException) {
            Log.e(SDK.TAG, e.getMessage(), e)
        }
        return this
    }

    /**
     * Вставка товара
     */
    fun put(item: Item?): Params? {
        try {
            val array: JSONArray?
            if (params.has("items")) {
                array = params.getJSONArray("items")
            } else {
                array = JSONArray()
                params.put("items", array)
            }
            val `object`: JSONObject = JSONObject()
            for (entry in item.columns.entrySet()) {
                `object`.put(entry.getKey(), entry.getValue())
            }
            array.put(`object`)
        } catch (e: JSONException) {
            Log.e(SDK.TAG, e.getMessage(), e)
        }

        return this
    }

    //---------------Private---------->
    internal enum class InternalParameter(protected var value: String?) : ParamInterface {
        SEARCH_TYPE("type"),
        SEARCH_QUERY("search_query"),
        RECOMMENDER_TYPE("recommender_type"),
        RECOMMENDER_CODE("recommender_code"),
        EVENT("event"),
        CATEGORY("category"),
        LABEL("label"),
        VALUE("value"),
        RECOMMENDED_BY("recommended_by"),
        RECOMMENDED_CODE("recommended_code"),
        EMAIL("email"),
        PHONE("phone"),
        PROPERTIES("properties"),
        ;

        @Override
        fun getValue(): String? {
            return value
        }
    }
}
