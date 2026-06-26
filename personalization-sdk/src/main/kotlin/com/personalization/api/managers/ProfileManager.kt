package com.personalization.api.managers

import com.personalization.api.responses.profile.GetProfileResponse

interface ProfileManager {

    /**
     * Fetch the stored user profile (`GET /profile`).
     *
     * The shop and device are identified automatically by the SDK's configured
     * `shop_id` / `did`; no parameters are required.
     *
     * @param onSuccess Callback with the parsed profile
     * @param onError Callback for error
     */
    fun getProfile(
        onSuccess: (GetProfileResponse) -> Unit,
        onError: (Int, String?) -> Unit = { _: Int, _: String? -> }
    )
}
