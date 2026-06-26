package com.personalization.api.responses.loyalty

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

/**
 * Response for the `loyalty/members/join` request.
 *
 * The endpoint returns an envelope `{ "status": "success" | "fail", "payload": { ... } }`.
 * [payload] is intentionally kept as a raw [JsonObject] because its shape differs between
 * success and failure responses.
 */
data class LoyaltyJoinResponse(
    @SerializedName("status")
    val status: String? = null,
    @SerializedName("payload")
    val payload: JsonObject? = null
)
