package com.personalization.api.responses.product

import com.google.gson.annotations.SerializedName

data class Location(
    @SerializedName("id")
    val id: String,
    @SerializedName("name")
    val name: String?
)
