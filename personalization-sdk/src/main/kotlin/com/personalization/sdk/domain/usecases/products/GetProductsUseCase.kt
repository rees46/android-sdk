package com.personalization.sdk.domain.usecases.products

import com.personalization.sdk.domain.repositories.NetworkRepository
import javax.inject.Inject

class GetProductsUseCase @Inject constructor(
    private val networkRepository: NetworkRepository
) {

}
