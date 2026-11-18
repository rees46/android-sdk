package com.personalization.features.predict.impl

import com.personalization.api.OnApiCallbackListener
import com.personalization.api.managers.PredictManager
import com.personalization.api.params.PurchasePredictParams
import com.personalization.api.responses.predict.PurchasePredictResponse
import com.personalization.core.SdkError
import com.personalization.core.internal.LegacyErrors
import com.personalization.features.predict.PredictApiImpl
import com.personalization.sdk.domain.usecases.network.SendNetworkMethodUseCase
import org.json.JSONObject
import javax.inject.Inject

/**
 * [PredictManager] on the path of `sdk.predict` ([PredictApiImpl.PATH]), on both cores: the API host's
 * `predict/probability-to-purchase` answers `503`, its service is no longer deployed there. The answer's
 * `payload` becomes the released [PurchasePredictResponse].
 */
internal class PredictManagerImpl @Inject constructor(
    private val sendNetworkMethodUseCase: SendNetworkMethodUseCase
) : PredictManager {

    override fun getProbabilityToPurchase(
        params: PurchasePredictParams,
        onSuccess: (PurchasePredictResponse) -> Unit,
        onError: (Int, String?) -> Unit
    ) {
        // The contacts of [params] are not sent: the server reads the device's did first, which every
        // request carries, so it never read them.
        sendNetworkMethodUseCase.getAsync(
            PredictApiImpl.PATH,
            JSONObject(),
            object : OnApiCallbackListener() {
                override fun onSuccess(response: JSONObject?) {
                    val probability = PredictApiImpl.readProbability(response)
                    if (probability == null) {
                        onError(LegacyErrors.NO_ANSWER, SdkError.Decoding.message)
                        return
                    }
                    onSuccess(PurchasePredictResponse(probability = probability.probability, clientId = probability.clientId.orEmpty()))
                }

                override fun onError(code: Int, msg: String?) {
                    onError(code, msg)
                }
            }
        )
    }
}
