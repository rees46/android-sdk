package com.personalization.features.predict

import com.personalization.api.managers.PredictApi
import com.personalization.api.models.predict.PurchaseProbability
import com.personalization.core.BridgeApi
import com.personalization.core.CompletionListener
import com.personalization.core.internal.NamespaceCalls
import com.personalization.core.internal.doubleOrNull
import com.personalization.core.internal.nonBlankStringOrNull
import com.personalization.sdk.domain.models.NetworkMethod
import org.json.JSONObject

/**
 * [PredictApi] over the namespaces' path ([NamespaceCalls]), on the path of the mobile SDKs: the contract's.
 * On 2026-11-13 that path answered `503` to every request, and the dashboard's `/api/v3/cdp/users/...`
 * answered; which one stays is the backend's decision (В42 of the contract's questions).
 */
@OptIn(BridgeApi::class)
internal class PredictApiImpl(private val calls: NamespaceCalls) : PredictApi {

    override suspend fun getProbabilityToPurchase(): PurchaseProbability =
        calls.request(GET, NetworkMethod.GET(PATH), read = ::readProbability)

    override fun getProbabilityToPurchase(listener: CompletionListener<PurchaseProbability>) =
        calls.complete(listener) { getProbabilityToPurchase() }

    internal companion object {
        const val GET = "predict.getProbabilityToPurchase"
        const val PATH = "predict/probability-to-purchase"

        /** `{probability, client_id}`; null (`decoding`) without a numeric `probability`. */
        fun readProbability(body: Any?): PurchaseProbability? {
            if (body !is JSONObject) return null
            val probability = body.doubleOrNull("probability") ?: return null
            return PurchaseProbability(probability = probability, clientId = body.nonBlankStringOrNull("client_id"))
        }
    }
}
