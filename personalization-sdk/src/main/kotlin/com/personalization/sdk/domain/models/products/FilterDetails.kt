package com.personalization.sdk.domain.models.products

data class FilterDetails(
    val count: Int,
    val priority: Int,
    val ranges: List<Int>?,
    val values: List<FilterValue>
)