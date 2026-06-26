package com.personalization.api.responses.collection

import com.google.gson.annotations.SerializedName
import com.personalization.api.responses.product.Product

/**
 * Response for the `GET /collection/{id}` request.
 *
 * The endpoint returns a configured product collection as `{ "products": [...] }`.
 */
data class CollectionResponse(
    @SerializedName("products")
    val products: List<Product> = emptyList()
)
