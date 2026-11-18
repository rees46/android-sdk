package com.personalization.api.models.recommendations

import com.personalization.api.models.products.Product

/**
 * Where the customer is when a recommendation block shows: the block's rules pick its products with it.
 * A field left null is not sent; a list goes as one string joined with commas, and an empty list is not
 * sent.
 *
 * @property itemId the product on the screen: a product card, a cart line (`item_id`).
 * @property categoryId the category on the screen (`category`).
 * @property searchQuery the query on a search screen (`search_query`).
 * @property excludedItemIds products the block must not show (`exclude`).
 * @property limit how many products, from 1.
 * @property extended true for whole products in [RecommendationResponse.products]; false for their ids
 * only, in [RecommendationResponse.itemIds].
 * @property withLocations [Product.locationIds] for every product; only with [extended], otherwise the call
 * fails with `invalidArgument`.
 */
data class RecommendationRequest(
    val itemId: String? = null,
    val categoryId: String? = null,
    val searchQuery: String? = null,
    val excludedItemIds: List<String>? = null,
    val brands: List<String>? = null,
    val locations: List<String>? = null,
    val limit: Int? = null,
    val extended: Boolean = true,
    val withLocations: Boolean = false,
)

/**
 * The products of a recommendation block (`sdk.recommendations.get`). A block on pause answers with no
 * products: both lists are empty.
 *
 * @property id the block's id.
 * @property title the block's title.
 * @property link the link of the title, when the block has one.
 * @property products the products, with [RecommendationRequest.extended]; empty without it.
 * @property itemIds the products' ids, always: those of [products], or the ids alone without
 * [RecommendationRequest.extended].
 */
data class RecommendationResponse(
    val id: Int? = null,
    val title: String? = null,
    val link: String? = null,
    val products: List<Product>,
    val itemIds: List<String>,
)
