package com.personalization.api.managers

import com.personalization.api.models.products.Product
import com.personalization.api.models.profile.Profile
import com.personalization.api.models.profile.ProfileUpdateRequest
import com.personalization.api.responses.orders.Order
import com.personalization.core.BridgeApi
import com.personalization.core.CompletionListener
import com.personalization.core.SdkError
import com.personalization.core.SdkException

/**
 * The customer's profile — the `profile` namespace: `sdk.profile.get()`. The API finds the customer by the
 * device's `did`, which the SDK adds with the other common fields itself, and the SDK keeps none of the
 * personal data [set] sends. The shop's secret key goes only with the server method [getOrders], which the
 * caller passes it to.
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

    /**
     * The customer's orders (`GET /orders/by_user`) from [dateFrom] (`YYYY-MM-DD`; the server's default is a
     * month ago), by date and then the server's own id. The released [Order] model: sums come as strings.
     *
     * A server method: it takes the shop's secret key, [shopSecret], which the caller passes and the SDK
     * neither keeps nor writes to the log or hands to the app's network logger. A secret in an app's binary
     * gives whoever unpacks it the shop's server access — whether such methods stay in the SDK is decided in
     * DEV-4723. The request carries `shop_id` and none of the device's fields, so the customer is the one of
     * [did], [email], [phone], [loyaltyId] or [externalId], whichever are given: [did] goes only when passed
     * (the device's own is `SDK.getDid()`).
     *
     * @throws SdkException with [SdkError.InvalidArgument] for an empty [shopSecret], an empty id, no id at all
     * or a [dateFrom] that is no date `YYYY-MM-DD`, before any request; [SdkError.Http] `403` for a wrong
     * secret, `400` for a customer the server does not find; otherwise as [ProfileApi] says.
     */
    suspend fun getOrders(
        shopSecret: String,
        did: String? = null,
        email: String? = null,
        phone: String? = null,
        loyaltyId: String? = null,
        externalId: String? = null,
        dateFrom: String? = null,
    ): List<Order>

    /** [get] for Java and for code outside coroutines: [listener] gets its result once, on the main thread. */
    fun get(listener: CompletionListener<Profile>)

    /** [set] for Java and for code outside coroutines: [listener] gets its result once, on the main thread. */
    fun set(request: ProfileUpdateRequest, listener: CompletionListener<Unit>)

    /** [getLastOrder] for Java and for code outside coroutines: [listener] gets its result once, on the main thread. */
    fun getLastOrder(listener: CompletionListener<List<Product>>)

    /** [getOrders] for Java and for code outside coroutines: [listener] gets its result once, on the main thread. */
    fun getOrders(
        shopSecret: String,
        did: String?,
        email: String?,
        phone: String?,
        loyaltyId: String?,
        externalId: String?,
        dateFrom: String?,
        listener: CompletionListener<List<Order>>,
    )
}
