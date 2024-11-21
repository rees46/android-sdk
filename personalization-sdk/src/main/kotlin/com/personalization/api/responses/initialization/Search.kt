package com.personalization.api.responses.initialization

data class Search(
    val enabled: Boolean,
    val landing: String,
    val type: String,
    val settings: SearchSettings?
)
