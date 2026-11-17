package com.personalization.api.models.collections

import com.personalization.api.models.products.Product

/** The products of a collection the shop set up in its account (`sdk.collections.get`). */
data class CollectionResponse(
    val products: List<Product>,
)
