package com.personalizatio.api.entities.search

import com.google.gson.annotations.SerializedName
import com.personalizatio.api.entities.product.ProductEntity

data class SearchBlankEntity(
    @SerializedName("html")
    val html: String,
    @SerializedName("products")
    val products: List<ProductEntity>,
    @SerializedName("suggests")
    val suggests: List<SuggestEntity>
)
