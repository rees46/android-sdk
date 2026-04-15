package com.personalization.api.managers

import com.personalization.api.params.PurchasePredictParams
import com.personalization.api.responses.predict.PurchasePredictResponse

interface PredictManager {

    /**
     * GET predict/probability-to-purchase — predicted purchase probability for the current visitor.
     *
     * @param params Optional identifiers (email, phone, telegram_id, loyalty_id). At least one of
     * `did` (from SDK) or these fields is required by the API.
     */
    fun getProbabilityToPurchase(
        params: PurchasePredictParams = PurchasePredictParams(),
        onSuccess: (PurchasePredictResponse) -> Unit,
        onError: (Int, String?) -> Unit = { _, _ -> }
    )
}
