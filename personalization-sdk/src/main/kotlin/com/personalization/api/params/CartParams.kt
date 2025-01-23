package com.personalization.api.params

import com.personalization.AbstractParams

class CartParams : AbstractParams<CartParams>() {
    enum class Parameter(override val value: String) : ParamInterface {
        EMAIL("email"),
        PHONE("phone"),
        LOYALTY_ID("loyalty_id"),
        EXTERNAL_ID("external_id"),
        TELEGRAM_ID("telegram_id")
    }
}
