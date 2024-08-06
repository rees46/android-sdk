package com.personalization.api.responses.search

import com.google.gson.annotations.SerializedName
import com.personalization.api.responses.product.Product

data class SearchBlankResponse(
    @SerializedName("html")
    val html: String,
    @SerializedName("products")
    val products: List<Product>,
    @SerializedName("suggests")
    val suggests: List<Suggest>
)
