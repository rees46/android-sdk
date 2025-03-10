package com.personalization.sdk.data.models.dto.popUp

enum class Position(val value: String) {
    CENTERED("centered"),
    BOTTOM("fixed_bottom"),
    TOP("top"),
    UNKNOWN("");

    companion object {
        fun fromString(value: String): Position? {
            return entries.find { it.value == value }
        }
    }
}
