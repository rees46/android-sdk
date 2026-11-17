package com.personalization.api.models.profile

/** The customer's gender, as the API writes it: `m` or `f`. The profile and the loyalty program share it. */
enum class Gender(val value: String) {
    MALE("m"),
    FEMALE("f"),
}
