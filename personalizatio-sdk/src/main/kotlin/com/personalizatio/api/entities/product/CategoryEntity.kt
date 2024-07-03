package com.personalizatio.api.entities.product

import com.google.gson.annotations.SerializedName

data class CategoryEntity(
    @SerializedName("id")
    val id: String,
    @SerializedName("level")
    val level: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("name_with_parent")
    val nameWithParent: String,
    @SerializedName("parent_id")
    val parentId: String,
    @SerializedName("url")
    val url: String,
    @SerializedName("url_handle")
    val urlHandle: String
)
