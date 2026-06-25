package com.personalization.api.managers

import com.personalization.api.responses.collection.CollectionResponse

interface CollectionManager {

    /**
     * Fetch a configured product collection (`GET /collection/{id}`).
     *
     * The shop and device are identified automatically by the SDK's configured
     * `shop_id` / `did`. The collection id is configured in the dashboard.
     *
     * @param collectionId Collection id (path segment)
     * @param location Optional user location
     * @param email Optional user email
     * @param phone Optional user phone
     * @param externalId Optional user external id
     * @param loyaltyId Optional user loyalty id
     * @param onSuccess Callback with the parsed collection
     * @param onError Callback for error
     */
    fun getCollection(
        collectionId: String,
        location: String? = null,
        email: String? = null,
        phone: String? = null,
        externalId: String? = null,
        loyaltyId: String? = null,
        onSuccess: (CollectionResponse) -> Unit,
        onError: (Int, String?) -> Unit = { _: Int, _: String? -> }
    )
}
