package com.personalization.api.models.segments

/**
 * A segment the customer is in (`sdk.segments.get`).
 *
 * @property type `dynamic`, `static`, `less_often`, `rfm`, `rfm3` or `unknown`; a string, not an enum, so a
 * kind of segment the server adds later reads as it is.
 */
data class Segment(
    val id: Int,
    val type: String? = null,
)
