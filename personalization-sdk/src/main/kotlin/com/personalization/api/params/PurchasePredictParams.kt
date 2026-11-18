package com.personalization.api.params

import org.json.JSONObject

private const val QUERY_EMAIL = "email"
private const val QUERY_PHONE = "phone"
private const val QUERY_TELEGRAM_ID = "telegram_id"
private const val QUERY_LOYALTY_ID = "loyalty_id"

/**
 * Contacts [com.personalization.api.managers.PredictManager.getProbabilityToPurchase] takes, and no longer
 * sends: the server finds the customer by the device's `did`, which the SDK sends with every request.
 */
data class PurchasePredictParams(
    val email: String? = null,
    val phone: String? = null,
    val telegramId: String? = null,
    val loyaltyId: String? = null
) {

    fun toQueryJson(): JSONObject {
        val json = JSONObject()
        email?.takeIf { it.isNotBlank() }?.let { json.put(QUERY_EMAIL, it) }
        phone?.takeIf { it.isNotBlank() }?.let { json.put(QUERY_PHONE, it) }
        telegramId?.takeIf { it.isNotBlank() }?.let { json.put(QUERY_TELEGRAM_ID, it) }
        loyaltyId?.takeIf { it.isNotBlank() }?.let { json.put(QUERY_LOYALTY_ID, it) }
        return json
    }
}
