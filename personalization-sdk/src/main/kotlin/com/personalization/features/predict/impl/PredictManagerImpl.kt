package com.personalization.features.predict.impl

import com.google.gson.Gson
import com.personalization.api.OnApiCallbackListener
import com.personalization.api.managers.PredictManager
import com.personalization.api.params.PurchasePredictParams
import com.personalization.api.responses.predict.PurchasePredictResponse
import com.personalization.sdk.domain.usecases.network.SendNetworkMethodUseCase
import org.json.JSONObject
import javax.inject.Inject

private const val PROBABILITY_TO_PURCHASE_PATH = "predict/probability-to-purchase"

internal class PredictManagerImpl @Inject constructor(
    private val sendNetworkMethodUseCase: SendNetworkMethodUseCase
) : PredictManager {

    override fun getProbabilityToPurchase(
        params: PurchasePredictParams,
        onSuccess: (PurchasePredictResponse) -> Unit,
        onError: (Int, String?) -> Unit
    ) {
        sendNetworkMethodUseCase.getAsync(
            PROBABILITY_TO_PURCHASE_PATH,
            params.toQueryJson(),
            object : OnApiCallbackListener() {
                override fun onSuccess(response: JSONObject?) {
                    if (response == null) {
                        onError(-1, null)
                        return
                    }
                    val parsed = Gson().fromJson(response.toString(), PurchasePredictResponse::class.java)
                    onSuccess(parsed)
                }

                override fun onError(code: Int, msg: String?) {
                    onError(code, msg)
                }
            }
        )
    }
}
