package com.personalization.api.responses.search

import com.google.gson.annotations.SerializedName
import com.personalization.api.responses.product.Product

data class SearchBlankResponse(
    @SerializedName("html")
    val html: String,
    @SerializedName("products")
    val products: List<Product>,
    @SerializedName("suggests")
    val suggests: List<Suggest>,
    @SerializedName("popular_categories")
    val popularCategories: List<PopularItem>?,
    @SerializedName("popular_brands")
    val popularBrands: List<PopularItem>?,
    @SerializedName("popular_links")
    val popularLinks: List<PopularItem>?
)
