package com.personalization.api.responses.orders

import com.personalization.api.responses.product.Product

/**
 * Response for the `orders/last_for_user` request.
 *
 * The endpoint returns a top-level JSON array of products that belong to the
 * user's last order, so [products] is mapped directly from that array.
 */
data class GetLastOrderProductsResponse(
    val products: List<Product>
)
