package com.personalization.sdk.domain.models.products

data class Category(
    val id: String,
    val name: String,
    val url: String,
    val urlHandle: String,
    val count: Int,
    val parent: String?,
    val alias: String?
)