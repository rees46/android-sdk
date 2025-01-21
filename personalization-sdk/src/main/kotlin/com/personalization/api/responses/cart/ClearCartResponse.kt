package com.personalization.api.responses.cart

import com.google.gson.annotations.SerializedName

private const val SUCCESS = "success"

data class ClearCartResponse(
    @SerializedName("status") val status: String
) {
    internal fun isSuccess(): Boolean {
        return status == SUCCESS
    }
}
