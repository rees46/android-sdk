package com.personalization.sdk.data.repositories.nps

import com.personalization.api.OnApiCallbackListener
import com.personalization.sdk.domain.repositories.NPSRepository
import com.personalization.sdk.domain.usecases.network.SendNetworkMethodUseCase
import org.json.JSONObject
import javax.inject.Inject

private const val METHOD_CREATE = "nps/create"
private const val KEY_RATE = "rate"
private const val KEY_CHANNEL = "channel"
private const val KEY_CATEGORY = "category"
private const val KEY_ORDER_ID = "order_id"
private const val KEY_COMMENT = "comment"

class NPSRepositoryImpl @Inject constructor(
    private val sendNetworkMethodUseCase: SendNetworkMethodUseCase
) : NPSRepository {

    override suspend fun review(
        rate: Int,
        channel: String,
        category: String,
        orderId: String?,
        comment: String?,
        listener: OnApiCallbackListener?
    ) {
        val params = JSONObject().apply {
            put(KEY_RATE, rate)
            put(KEY_CHANNEL, channel)
            put(KEY_CATEGORY, category)
            orderId?.let { put(KEY_ORDER_ID, it) }
            comment?.let { put(KEY_COMMENT, it) }
        }

        sendNetworkMethodUseCase.postAsync(
            method = METHOD_CREATE,
            params = params,
            listener = listener
        )
    }
}
