package com.personalization.api.managers

import com.personalization.api.responses.loyalty.LoyaltyJoinResponse
import com.personalization.api.responses.loyalty.LoyaltyStatusResponse

interface LoyaltyManager {

    /**
     * Join the loyalty program (`loyalty/members/join`).
     *
     * The shop is identified automatically by the SDK's configured `shop_id`; only the member
     * fields are passed here. [phone] is required by the endpoint.
     *
     * @param phone Member phone number (required)
     * @param email Member email (optional)
     * @param firstName Member first name (optional)
     * @param lastName Member last name (optional)
     * @param onSuccess Callback with the parsed join response
     * @param onError Callback for error
     */
    fun join(
        phone: String,
        email: String? = null,
        firstName: String? = null,
        lastName: String? = null,
        onSuccess: (LoyaltyJoinResponse) -> Unit,
        onError: (Int, String?) -> Unit = { _: Int, _: String? -> }
    )

    /**
     * Request the loyalty membership status (`loyalty/members/status`).
     *
     * The shop is identified automatically by the SDK's configured `shop_id`.
     *
     * @param identifier Member identifier (phone)
     * @param onSuccess Callback with the parsed status response
     * @param onError Callback for error
     */
    fun getStatus(
        identifier: String,
        onSuccess: (LoyaltyStatusResponse) -> Unit,
        onError: (Int, String?) -> Unit = { _: Int, _: String? -> }
    )
}
