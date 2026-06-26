package com.personalization.features.profile.impl

import com.google.gson.Gson
import com.personalization.Params
import com.personalization.api.OnApiCallbackListener
import com.personalization.api.managers.ProfileManager
import com.personalization.api.responses.profile.GetProfileResponse
import com.personalization.sdk.domain.usecases.network.SendNetworkMethodUseCase
import javax.inject.Inject
import org.json.JSONObject

internal class ProfileManagerImpl @Inject constructor(
    private val sendNetworkMethodUseCase: SendNetworkMethodUseCase
) : ProfileManager {

    override fun getProfile(
        onSuccess: (GetProfileResponse) -> Unit,
        onError: (Int, String?) -> Unit
    ) {
        // shop_id and did are injected automatically by the network layer.
        sendNetworkMethodUseCase.getAsync(
            GET_PROFILE_REQUEST,
            Params().build(),
            object : OnApiCallbackListener() {
                override fun onSuccess(response: JSONObject?) {
                    val parsed = Gson().fromJson(
                        response.toString(),
                        GetProfileResponse::class.java
                    )
                    onSuccess(parsed ?: GetProfileResponse())
                }

                override fun onError(code: Int, msg: String?) {
                    onError(code, msg)
                }
            }
        )
    }

    companion object {
        const val GET_PROFILE_REQUEST = "profile"
    }
}
