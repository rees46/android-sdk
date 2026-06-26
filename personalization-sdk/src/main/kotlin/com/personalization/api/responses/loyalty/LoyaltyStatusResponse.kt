package com.personalization.api.responses.loyalty

import com.google.gson.annotations.SerializedName

/**
 * Response for the `loyalty/members/status` request.
 *
 * Envelope: `{ "status": "success" | "fail", "payload": { "member": ..., "level": { ... } } }`.
 */
data class LoyaltyStatusResponse(
    @SerializedName("status")
    val status: String? = null,
    @SerializedName("payload")
    val payload: LoyaltyStatusPayload? = null
)

data class LoyaltyStatusPayload(
    @SerializedName("member")
    val member: Boolean? = null,
    @SerializedName("level")
    val level: LoyaltyLevel? = null
)

data class LoyaltyLevel(
    @SerializedName("name")
    val name: String? = null,
    @SerializedName("code")
    val code: String? = null,
    @SerializedName("expiration_date")
    val expirationDate: String? = null
)
