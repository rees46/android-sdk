package com.personalization.features.products.impl

import com.personalization.api.OnApiCallbackListener
import com.personalization.api.managers.ProductsManager
import com.personalization.sdk.domain.usecases.network.SendNetworkMethodUseCase
import javax.inject.Inject

internal class ProductsManagerImpl @Inject constructor(
    private val sendNetworkMethodUseCase: SendNetworkMethodUseCase
) : ProductsManager {

    override fun getProductsList(
        brands: String?,
        merchants: String?,
        categories: String?,
        locations: String?,
        limit: Int?,
        page: Int?,
        filters: Map<String, Any>?,
        listener: OnApiCallbackListener?
    ) {
        TODO("Not yet implemented")
    }
}
