package com.personalization.api.managers

import com.personalization.api.params.PurchasePredictParams
import com.personalization.api.responses.predict.PurchasePredictResponse

interface PredictManager {

    /**
     * The predicted purchase probability for the customer of this device (`GET
     * /api/v3/cdp/users/probability-to-purchase` on `Rees46Config.appDomain`, `app.rees46.ru` unless the shop's
     * cluster has its own). It waits for the device's `did`. A customer the server does not know has
     * `probability` 0 and an empty `clientId`.
     *
     * [onError] gets the HTTP code of an answer outside `2xx` (`400` without a `did`, `402` for a shop that
     * does not pay or has no `cdp` service, `403` for an inactive or restricted one, `404` for an unknown
     * one) with its body, and `-1` for an answer without a numeric `probability` or a failure without one.
     *
     * @param params Not sent: the server finds the customer by the device's `did`, which the SDK sends.
     */
    fun getProbabilityToPurchase(
        params: PurchasePredictParams = PurchasePredictParams(),
        onSuccess: (PurchasePredictResponse) -> Unit,
        onError: (Int, String?) -> Unit = { _, _ -> }
    )
}
