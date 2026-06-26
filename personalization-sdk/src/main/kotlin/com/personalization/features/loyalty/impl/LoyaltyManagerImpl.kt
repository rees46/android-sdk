package com.personalization.features.loyalty.impl

import com.google.gson.Gson
import com.personalization.Params
import com.personalization.api.OnApiCallbackListener
import com.personalization.api.managers.LoyaltyManager
import com.personalization.api.responses.loyalty.LoyaltyJoinResponse
import com.personalization.api.responses.loyalty.LoyaltyStatusResponse
import com.personalization.sdk.domain.usecases.network.SendNetworkMethodUseCase
import javax.inject.Inject
import org.json.JSONObject

internal class LoyaltyManagerImpl @Inject constructor(
    private val sendNetworkMethodUseCase: SendNetworkMethodUseCase
) : LoyaltyManager {

    override fun join(
        phone: String,
        email: String?,
        firstName: String?,
        lastName: String?,
        onSuccess: (LoyaltyJoinResponse) -> Unit,
        onError: (Int, String?) -> Unit
    ) {
        val params = Params()
        params.put(PHONE_PARAM, phone)
        email?.let { params.put(EMAIL_PARAM, it) }
        firstName?.let { params.put(FIRST_NAME_PARAM, it) }
        lastName?.let { params.put(LAST_NAME_PARAM, it) }

        sendNetworkMethodUseCase.postAsync(
            JOIN_REQUEST,
            params.build(),
            object : OnApiCallbackListener() {
                override fun onSuccess(response: JSONObject?) {
                    val parsed = Gson().fromJson(
                        response.toString(),
                        LoyaltyJoinResponse::class.java
                    )
                    onSuccess(parsed ?: LoyaltyJoinResponse())
                }

                override fun onError(code: Int, msg: String?) {
                    onError(code, msg)
                }
            }
        )
    }

    override fun getStatus(
        identifier: String,
        onSuccess: (LoyaltyStatusResponse) -> Unit,
        onError: (Int, String?) -> Unit
    ) {
        val params = Params()
        params.put(IDENTIFIER_PARAM, identifier)

        sendNetworkMethodUseCase.getAsync(
            STATUS_REQUEST,
            params.build(),
            object : OnApiCallbackListener() {
                override fun onSuccess(response: JSONObject?) {
                    val parsed = Gson().fromJson(
                        response.toString(),
                        LoyaltyStatusResponse::class.java
                    )
                    onSuccess(parsed ?: LoyaltyStatusResponse())
                }

                override fun onError(code: Int, msg: String?) {
                    onError(code, msg)
                }
            }
        )
    }

    companion object {
        const val JOIN_REQUEST = "loyalty/members/join"
        const val STATUS_REQUEST = "loyalty/members/status"

        private const val PHONE_PARAM = "phone"
        private const val EMAIL_PARAM = "email"
        private const val FIRST_NAME_PARAM = "first_name"
        private const val LAST_NAME_PARAM = "last_name"
        private const val IDENTIFIER_PARAM = "identifier"
    }
}
