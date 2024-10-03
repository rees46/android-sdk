package com.personalization.features.catalog

import com.personalization.api.managers.CatalogManager
import javax.inject.Inject

internal class CatalogManagerImpl @Inject constructor(

) : CatalogManager {

    override fun getProductsList(
        brands: String?,
        merchants: String?,
        categories: String?,
        locations: String?,
        limit: Int?,
        page: Int?,
        filters: Map<String, Any>?
    ) {
        TODO("Not yet implemented")
    }
}
