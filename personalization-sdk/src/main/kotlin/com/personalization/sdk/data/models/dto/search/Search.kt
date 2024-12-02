package com.personalization.sdk.data.models.dto.search

data class Search(
    val enabled: Boolean,
    val landing: String,
    val type: String,
    val settings: SearchSettings?
)
