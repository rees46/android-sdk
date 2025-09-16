package com.personalization.sdk.domain.repositories

import com.personalization.api.OnApiCallbackListener

interface NPSRepository {
    fun review(
        rate: Int,
        channel: String,
        category: String,
        orderId: String?,
        comment: String?,
        listener: OnApiCallbackListener? = null
    )
}
