package com.personalization.features.collection.impl

import com.google.gson.Gson
import com.personalization.Params
import com.personalization.api.OnApiCallbackListener
import com.personalization.api.managers.CollectionManager
import com.personalization.api.responses.collection.CollectionResponse
import com.personalization.sdk.domain.usecases.network.SendNetworkMethodUseCase
import javax.inject.Inject
import org.json.JSONObject

internal class CollectionManagerImpl @Inject constructor(
    private val sendNetworkMethodUseCase: SendNetworkMethodUseCase
) : CollectionManager {

    override fun getCollection(
        collectionId: String,
        location: String?,
        email: String?,
        phone: String?,
        externalId: String?,
        loyaltyId: String?,
        onSuccess: (CollectionResponse) -> Unit,
        onError: (Int, String?) -> Unit
    ) {
        val params = Params()
        location?.let { params.put(LOCATION_KEY, it) }
        email?.let { params.put(EMAIL_KEY, it) }
        phone?.let { params.put(PHONE_KEY, it) }
        externalId?.let { params.put(EXTERNAL_ID_KEY, it) }
        loyaltyId?.let { params.put(LOYALTY_ID_KEY, it) }

        sendNetworkMethodUseCase.getAsync(
            "$COLLECTION_REQUEST/$collectionId",
            params.build(),
            object : OnApiCallbackListener() {
                override fun onSuccess(response: JSONObject?) {
                    val parsed = Gson().fromJson(
                        response.toString(),
                        CollectionResponse::class.java
                    )
                    onSuccess(parsed ?: CollectionResponse())
                }

                override fun onError(code: Int, msg: String?) {
                    onError(code, msg)
                }
            }
        )
    }

    companion object {
        const val COLLECTION_REQUEST = "collection"

        private const val LOCATION_KEY = "location"
        private const val EMAIL_KEY = "email"
        private const val PHONE_KEY = "phone"
        private const val EXTERNAL_ID_KEY = "external_id"
        private const val LOYALTY_ID_KEY = "loyalty_id"
    }
}
