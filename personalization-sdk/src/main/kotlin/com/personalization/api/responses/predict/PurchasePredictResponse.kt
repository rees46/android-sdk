package com.personalization.api.responses.predict

import com.google.gson.annotations.SerializedName

/**
 * The answer of [com.personalization.api.managers.PredictManager.getProbabilityToPurchase]: the `payload`
 * of `{"status": "success", "payload": {"probability", "client_id"}}`.
 */
data class PurchasePredictResponse(
    /** From 0 to 1, to hundredths. */
    @SerializedName("probability")
    val probability: Double,
    /** The id of the customer the server found (a number in the answer); empty when none. */
    @SerializedName("client_id")
    val clientId: String
)
