package com.personalization.api.models.predict

/**
 * How likely the customer of this device is to buy (`sdk.predict.getProbabilityToPurchase()`).
 *
 * A customer the server does not know has [probability] 0 and no [clientId]; one it knows without the
 * events the forecast reads has [probability] 0 and its [clientId].
 */
data class PurchaseProbability(
    /** From 0 to 1, to hundredths. */
    val probability: Double,
    /** The id of the customer the server found (`client_id`, a number in the answer); null when none. */
    val clientId: String?,
)
