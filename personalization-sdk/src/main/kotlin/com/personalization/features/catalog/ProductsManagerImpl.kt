package com.personalization.features.catalog

import com.personalization.api.managers.ProductsManager
import javax.inject.Inject

internal class ProductsManagerImpl @Inject constructor(

) : ProductsManager {

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
