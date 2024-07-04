package com.personalizatio.api.entities.categories.category

import com.google.gson.annotations.SerializedName

data class Category(
    @SerializedName("count")
    val count: Int,
    @SerializedName("id")
    val id: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("url")
    val url: String,
    @SerializedName("url_handle")
    val urlHandle: String
)
