package com.personalizatio.api.entities.search

import com.google.gson.annotations.SerializedName

data class SuggestEntity(
    @SerializedName("name")
    val name: String,
    @SerializedName("url")
    val url: String
)
