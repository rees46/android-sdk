package com.personalization.api.responses.profile

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

/**
 * Response for the `GET /profile` request.
 *
 * The endpoint returns the stored user profile. Only the most common fields are
 * typed here; the open-ended `custom_properties` map is kept as a raw [JsonObject]
 * because its shape is shop-specific.
 */
data class GetProfileResponse(
    @SerializedName("id")
    val id: String? = null,
    @SerializedName("email")
    val email: String? = null,
    @SerializedName("phone")
    val phone: String? = null,
    @SerializedName("first_name")
    val firstName: String? = null,
    @SerializedName("last_name")
    val lastName: String? = null,
    @SerializedName("has_email")
    val hasEmail: Boolean? = null,
    @SerializedName("email_registered_at")
    val emailRegisteredAt: String? = null,
    @SerializedName("gender")
    val gender: String? = null,
    @SerializedName("computed_gender")
    val computedGender: String? = null,
    @SerializedName("bought_something")
    val boughtSomething: Boolean? = null,
    @SerializedName("custom_properties")
    val customProperties: JsonObject? = null
)
