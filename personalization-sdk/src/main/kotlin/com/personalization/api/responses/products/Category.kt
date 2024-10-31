package com.personalization.api.responses.products

import com.google.gson.annotations.SerializedName

data class Category(
    @SerializedName("id")
    val id: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("url")
    val url: String,
    @SerializedName("url_handle")
    val urlHandle: String,
    @SerializedName("count")
    val count: Int,
    @SerializedName("parent")
    val parent: String?,
    @SerializedName("alias")
    val alias: String?
)
