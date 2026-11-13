package com.personalization

import com.personalization.logs.SdkLogs
import com.personalization.logs.LogCodes
import android.text.TextUtils
import org.json.JSONException
import org.json.JSONObject

abstract class AbstractParams<P : AbstractParams<P>> {
    protected val params: JSONObject = JSONObject()

    interface ParamInterface {
        val value: String
    }

    /**
     * Вставка строковых параметров
     */
    fun put(param: ParamInterface, value: String): P {
        try {
            params.put(param.value, value)
        } catch (e: JSONException) {
            SdkLogs.log(LogLevel.ERROR, LogCodes.REQUEST_BUILD_FAILED, "Request parameters could not be built", throwable = e)
        }
        return this as P
    }

    fun put(param: ParamInterface, value: Int): P {
        return put(param, value.toString())
    }

    fun put(param: ParamInterface, value: Boolean): P {
        try {
            params.put(param.value, value)
        } catch (e: JSONException) {
            SdkLogs.log(LogLevel.ERROR, LogCodes.REQUEST_BUILD_FAILED, "Request parameters could not be built", throwable = e)
        }
        return this as P
    }

    fun put(param: ParamInterface, value: JSONObject): P {
        try {
            params.put(param.value, value)
        } catch (e: JSONException) {
            SdkLogs.log(LogLevel.ERROR, LogCodes.REQUEST_BUILD_FAILED, "Request parameters could not be built", throwable = e)
        }
        return this as P
    }


    /**
     * Вставка параметров с массивом
     */
    fun put(param: ParamInterface, value: Array<String>): P {
        try {
            params.put(param.value, TextUtils.join(",", value))
        } catch (e: JSONException) {
            SdkLogs.log(LogLevel.ERROR, LogCodes.REQUEST_BUILD_FAILED, "Request parameters could not be built", throwable = e)
        }
        return this as P
    }

    internal fun put(param: String, value: String): P {
        params.put(param, value)
        return this as P
    }

    internal fun put(param: String, value: Int): P {
        params.put(param, value)
        return this as P
    }

    internal fun put(param: String, value: Boolean): P {
        params.put(param, value)
        return this as P
    }

    fun build(): JSONObject {
        return params
    }
}
