package com.personalization.api.managers

import com.personalization.api.models.predict.PurchaseProbability
import com.personalization.core.BridgeApi
import com.personalization.core.CompletionListener
import com.personalization.core.SdkError
import com.personalization.core.SdkException

/**
 * Forecasts of the customer's behaviour — the `predict` namespace: `sdk.predict.getProbabilityToPurchase()`.
 * The customer is the device's `did`, which the SDK adds itself; contacts are not sent.
 *
 * For the Flutter and React Native bridges: it runs on the new core only (`Rees46.initializeBridge`). On
 * an instance of the legacy core every call fails with [SdkError.NotInitialized] without a request.
 */
@BridgeApi
interface PredictApi {

    /**
     * How likely the customer is to buy (`GET /api/v3/cdp/users/probability-to-purchase` on
     * `Rees46Config.appDomain`), from the products they viewed, put in the cart or in favorites over the last
     * days. It waits for the instance's first `/init`, which gives the device its `did`.
     *
     * @throws SdkException with [SdkError.NotInitialized] (the legacy core, an instance not initialized or
     * released), [SdkError.ShopBlocked], [SdkError.Network], [SdkError.Timeout], [SdkError.Http] (`400`
     * without a `did`, `402` for a shop that does not pay or has no `cdp` service, `403` for an inactive or
     * restricted one, `404` for an unknown one), [SdkError.Decoding] (an answer without a numeric
     * `probability`) or [SdkError.Cancelled] (`release()`).
     */
    suspend fun getProbabilityToPurchase(): PurchaseProbability

    /** [getProbabilityToPurchase] for Java and for code outside coroutines: [listener] gets its result once, on the main thread. */
    fun getProbabilityToPurchase(listener: CompletionListener<PurchaseProbability>)
}
