package com.personalization.api.responses.search

import com.google.gson.annotations.SerializedName

data class PopularItem(
    @SerializedName("name")
    val name: String,
    @SerializedName("url")
    val url: String
)
