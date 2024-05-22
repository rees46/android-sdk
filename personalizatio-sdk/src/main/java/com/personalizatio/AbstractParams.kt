package com.personalizatio

import android.text.TextUtils

internal abstract class AbstractParams<P : AbstractParams<P?>?> {
    protected val params: JSONObject? = JSONObject()

    interface ParamInterface {
        fun getValue(): String?
    }

    /**
     * Вставка строковых параметров
     */
    fun put(param: ParamInterface?, value: String?): P? {
        try {
            params.put(param.getValue(), value)
        } catch (e: JSONException) {
            Log.e(SDK.TAG, e.getMessage(), e)
        }
        return this as P
    }

    fun put(param: ParamInterface?, value: Int): P? {
        return put(param, String.valueOf(value))
    }

    fun put(param: ParamInterface?, value: Boolean): P? {
        try {
            params.put(param.getValue(), value)
        } catch (e: JSONException) {
            Log.e(SDK.TAG, e.getMessage(), e)
        }
        return this as P
    }

    fun put(param: ParamInterface?, value: JSONObject?): P? {
        try {
            params.put(param.getValue(), value)
        } catch (e: JSONException) {
            Log.e(SDK.TAG, e.getMessage(), e)
        }
        return this as P
    }


    /**
     * Вставка параметров с массивом
     */
    fun put(param: ParamInterface?, value: Array<String?>?): P? {
        try {
            params.put(param.getValue(), TextUtils.join(",", value))
        } catch (e: JSONException) {
            Log.e(SDK.TAG, e.getMessage(), e)
        }
        return this as P
    }

    fun build(): JSONObject? {
        return params
    }
}
