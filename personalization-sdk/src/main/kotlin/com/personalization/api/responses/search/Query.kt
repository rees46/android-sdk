package com.personalization.api.responses.search

import com.google.gson.annotations.SerializedName

/** Search phrase suggested for the typed text (`queries` of an instant search). */
data class Query(
    @SerializedName("name")
    val name: String,
    @SerializedName("url")
    val url: String? = null
)
