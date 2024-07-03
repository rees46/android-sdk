package com.personalizatio.api.entities.search

import com.google.gson.annotations.SerializedName
import com.personalizatio.api.entities.product.ProductEntity

data class SearchInstantEntity(
    @SerializedName("book_author")
    val bookAuthor: List<Any>,
    @SerializedName("categories")
    val categories: List<CategoryEntity>,
    @SerializedName("clarification")
    val clarification: Boolean,
    @SerializedName("collections")
    val collections: List<Any>,
    @SerializedName("html")
    val html: String,
    @SerializedName("products")
    val products: List<ProductEntity>,
    @SerializedName("products_total")
    val productsTotal: Int,
    @SerializedName("queries")
    val queries: List<Any>,
    @SerializedName("requests_count")
    val requestsCount: Int,
    @SerializedName("search_query")
    val searchQuery: String
)
