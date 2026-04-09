package com.personalization.features.trackEvent.impl

import com.personalization.sdk.data.models.params.UserBasicParams
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Shared rules for custom event `customFields` merge and validation (parity with iOS trackEvent).
 */
internal object TrackCustomEventPayloadHelper {

    const val CLIENT_VALIDATION_ERROR_CODE: Int = -1

    private const val KEY_EVENT = "event"
    private const val KEY_TIME = "time"
    private const val KEY_CATEGORY = "category"
    private const val KEY_LABEL = "label"
    private const val KEY_VALUE = "value"
    private const val KEY_SOURCE = "source"
    private const val KEY_PAYLOAD = "payload"
    private const val KEY_FROM = "from"
    private const val KEY_CODE = "code"
    private const val KEY_STREAM = "stream"

    val RESERVED_CUSTOM_EVENT_KEYS: Set<String> = setOf(
        UserBasicParams.SHOP_ID,
        UserBasicParams.DID,
        UserBasicParams.SEANCE,
        UserBasicParams.SID,
        UserBasicParams.SEGMENT,
        KEY_STREAM,
        KEY_EVENT,
        KEY_TIME,
        KEY_CATEGORY,
        KEY_LABEL,
        KEY_VALUE,
        KEY_SOURCE,
        KEY_PAYLOAD,
        KEY_FROM,
        KEY_CODE,
    )

    fun effectiveCustomFields(map: Map<String, Any?>?): Map<String, Any> {
        if (map.isNullOrEmpty()) return emptyMap()
        val out = LinkedHashMap<String, Any>()
        for ((key, value) in map) {
            if (key.isBlank() || value == null) continue
            out[key] = value
        }
        return out
    }

    /**
     * @return error message for [listener.onError], or null if valid
     */
    fun validateNoReservedKeyCollisions(customFields: Map<String, Any>): String? {
        if (customFields.isEmpty()) return null
        val collisions = customFields.keys.intersect(RESERVED_CUSTOM_EVENT_KEYS)
        if (collisions.isEmpty()) return null
        val sorted = collisions.toSortedSet().joinToString(", ")
        return "trackEvent: customFields contains reserved keys: $sorted"
    }

    @Throws(JSONException::class)
    fun putJsonValue(target: JSONObject, key: String, value: Any) {
        when (value) {
            is String -> target.put(key, value)
            is Int -> target.put(key, value)
            is Long -> target.put(key, value)
            is Double -> target.put(key, value)
            is Float -> target.put(key, value.toDouble())
            is Boolean -> target.put(key, value)
            is JSONObject -> target.put(key, value)
            is JSONArray -> target.put(key, value)
            else -> target.put(key, value.toString())
        }
    }
}
