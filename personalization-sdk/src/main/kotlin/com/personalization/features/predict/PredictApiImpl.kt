package com.personalization.features.predict

import com.personalization.api.managers.PredictApi
import com.personalization.api.models.predict.PurchaseProbability
import com.personalization.core.BridgeApi
import com.personalization.core.CompletionListener
import com.personalization.core.internal.NamespaceCalls
import com.personalization.core.internal.doubleOrNull
import com.personalization.core.internal.nonBlankStringOrNull
import com.personalization.core.internal.objectOrNull
import com.personalization.sdk.domain.models.NetworkMethod
import org.json.JSONObject

/**
 * [PredictApi] over the namespaces' path ([NamespaceCalls]), on API v3 of the shop's account
 * (`Rees46Config.appDomain`): the API host's `predict/probability-to-purchase` answers `503`, its service
 * is no longer deployed there (В42 of the contract's questions).
 */
@OptIn(BridgeApi::class)
internal class PredictApiImpl(private val calls: NamespaceCalls) : PredictApi {

    override suspend fun getProbabilityToPurchase(): PurchaseProbability =
        calls.request(GET, NetworkMethod.GET(PATH), read = ::readProbability)

    override fun getProbabilityToPurchase(listener: CompletionListener<PurchaseProbability>) =
        calls.complete(listener) { getProbabilityToPurchase() }

    internal companion object {
        const val GET = "predict.getProbabilityToPurchase"
        const val PATH = "api/v3/cdp/users/probability-to-purchase"

        /** `{"status": "success", "payload": {probability, client_id}}`; null (`decoding`) without a numeric `payload.probability`. */
        fun readProbability(body: Any?): PurchaseProbability? {
            val payload = (body as? JSONObject)?.objectOrNull("payload") ?: return null
            val probability = payload.doubleOrNull("probability") ?: return null
            return PurchaseProbability(probability = probability, clientId = payload.nonBlankStringOrNull("client_id"))
        }
    }
}
