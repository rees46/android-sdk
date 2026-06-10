package com.personalization

/**
 * Supported push notification providers.
 *
 * @property id stable public/internal identifier, also used as the per-provider preferences key
 * suffix. Tokens are stored per provider and are not interchangeable.
 * @property serverValue the value the rees46 backend expects in the `push_provider` field of
 * `mobile_push_tokens`. It differs from [id]: the backend identifies the delivery channel by its
 * vendor name (`firebase`, `huawei`), not by the abbreviation used in client code.
 */
enum class PushProvider(val id: String, val serverValue: String) {
    FCM(id = "fcm", serverValue = "firebase"),
    HMS(id = "hms", serverValue = "huawei");

    companion object {
        fun fromId(id: String): PushProvider? = entries.firstOrNull { it.id == id }
    }
}
