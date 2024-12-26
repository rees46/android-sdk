package com.personalization.api.params

import org.json.JSONObject
import java.util.Date

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

        fun put(param: String, value: Date): Builder {
            params.put(param, value)
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
            params.put(param, value)
            return this
        }

        fun put(param: String, value: Array<Int>): Builder {
            params.put(param, value)
            return this
        }

        fun put(param: String, value: Array<Float>): Builder {
            params.put(param, value)
            return this
        }

        fun put(param: String, value: Array<Date>): Builder {
            params.put(param, value)
            return this
        }

        fun put(param: String, value: Array<Boolean>): Builder {
            params.put(param, value)
            return this
        }

        fun put(param: String, value: Array<JSONObject>): Builder {
            params.put(param, value)
            return this
        }

        fun build(): ProfileParams {
            return ProfileParams(params)
        }
    }

    fun toJson(): JSONObject = params
}
