package com.personalization.sdk.domain.repositories

interface ProductsRepository {

    fun getProductsList(
        brands: String?,
        merchants: String?,
        categories: String?,
        locations: String?,
        limit: Int?,
        page: Int?,
        filters: Map<String, Any>?
    )

}
