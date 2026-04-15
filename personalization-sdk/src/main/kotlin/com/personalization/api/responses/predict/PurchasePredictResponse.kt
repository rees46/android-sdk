package com.personalization.api.responses.predict

import com.google.gson.annotations.SerializedName

data class PurchasePredictResponse(
    @SerializedName("probability")
    val probability: Double,
    @SerializedName("client_id")
    val clientId: String
)
