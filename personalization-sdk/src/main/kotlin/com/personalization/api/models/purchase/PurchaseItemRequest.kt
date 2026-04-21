package com.personalization.api.models.purchase

/**
 * One purchased line item (strict mobile contract).
 *
 * Required: [id], [amount], [price].
 * Optional: [quantity], [lineId], [fashionSize] — omit from the constructor when not needed (defaults).
 */
data class PurchaseItemRequest(
    val id: String,
    val amount: Int,
    val price: Double,
    val quantity: Int? = null,
    val lineId: String? = null,
    val fashionSize: String? = null,
)
