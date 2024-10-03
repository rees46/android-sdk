package com.personalization.api.managers

interface CatalogManager {

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
