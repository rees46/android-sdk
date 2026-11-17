package com.personalization.api.managers

import com.personalization.api.models.products.Product
import com.personalization.api.models.profile.Profile
import com.personalization.api.models.profile.ProfileUpdateRequest
import com.personalization.core.BridgeApi
import com.personalization.core.CompletionListener
import com.personalization.core.SdkError
import com.personalization.core.SdkException

/**
 * The customer's profile — the `profile` namespace: `sdk.profile.get()`. The API finds the customer by the
 * device's `did`, which the SDK adds with the other common fields itself; the SDK never sends the shop's
 * secret key, and keeps none of the personal data [set] sends.
 *
 * For the Flutter and React Native bridges: it runs on the new core only (`Rees46.initializeBridge`). On
 * an instance of the legacy core every call fails with [SdkError.NotInitialized] without a request.
 *
 * Every method throws [SdkException] with [SdkError.NotInitialized] (the legacy core, an instance not
 * initialized or released), [SdkError.ShopBlocked], [SdkError.InvalidArgument] (before any request),
 * [SdkError.Network], [SdkError.Timeout], [SdkError.Http], [SdkError.Decoding] or [SdkError.Cancelled]
 * (`release()`). Nothing repeats a request: whether to ask again is the app's choice.
 */
@BridgeApi
interface ProfileApi {

    /** The customer's profile (`GET /profile`); `http(404)` for a `did` the server does not know. */
    suspend fun get(): Profile

    /**
     * Writes the properties of [request] to the profile (`POST /profile/set`), not through the queue: the
     * result is the server's answer. Call it on sign-in, on a change of city and on every launch, with the
     * current id and city. See [ProfileUpdateRequest] for what fails with `invalidArgument`.
     */
    suspend fun set(request: ProfileUpdateRequest)

    /**
     * The products of the customer's last order (`GET /orders/last_for_user`); an empty list without
     * orders, `http(400)` for a `did` the server does not know.
     */
    suspend fun getLastOrder(): List<Product>

    /** [get] for Java and for code outside coroutines: [listener] gets its result once, on the main thread. */
    fun get(listener: CompletionListener<Profile>)

    /** [set] for Java and for code outside coroutines: [listener] gets its result once, on the main thread. */
    fun set(request: ProfileUpdateRequest, listener: CompletionListener<Unit>)

    /** [getLastOrder] for Java and for code outside coroutines: [listener] gets its result once, on the main thread. */
    fun getLastOrder(listener: CompletionListener<List<Product>>)
}
