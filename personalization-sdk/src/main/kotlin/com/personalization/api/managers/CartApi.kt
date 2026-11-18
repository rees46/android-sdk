package com.personalization.api.managers

import com.personalization.api.responses.cart.CartItem
import com.personalization.core.BridgeApi
import com.personalization.core.CompletionListener
import com.personalization.core.SdkError
import com.personalization.core.SdkException

/**
 * The customer's cart on the server — the `cart` namespace: `sdk.cart.get()`. The server builds the cart
 * from the cart events of [TrackingApi] (`addToCart`, `syncCart`, `removeFromCart`) and finds it by the
 * device's `did`; the SDK adds `did` and the other common fields itself. [clear] is a server method, with the
 * shop's secret key and without the device's fields.
 *
 * For the Flutter and React Native bridges: it runs on the new core only (`Rees46.initializeBridge`). On
 * an instance of the legacy core every call fails with [SdkError.NotInitialized] without a request.
 */
@BridgeApi
interface CartApi {

    /**
     * The cart's items (`GET /products/cart`); an empty cart is an empty list. An item without a product id
     * is left out, and one without a quantity counts as 1.
     *
     * @throws SdkException with [SdkError.NotInitialized] (the legacy core, an instance not initialized or
     * released), [SdkError.ShopBlocked], [SdkError.Network], [SdkError.Timeout], [SdkError.Http] (`404` for a
     * `did` the server does not know), [SdkError.Decoding] or [SdkError.Cancelled] (`release()`).
     */
    suspend fun get(): List<CartItem>

    /** [get] for Java and for code outside coroutines: [listener] gets its result once, on the main thread. */
    fun get(listener: CompletionListener<List<CartItem>>)

    /**
     * Empties the cart of the customer of [email], [phone], [loyaltyId], [externalId] or [telegramId], whichever
     * are given (`GET /products/cart/clear`, the method the server takes). The server finds the customer by
     * these alone, never by the device: the request carries `shop_id` and none of the device's fields.
     *
     * A server method: it takes the shop's secret key, [shopSecret], which the caller passes and the SDK
     * neither keeps nor writes to the log or hands to the app's network logger. A secret in an app's binary
     * gives whoever unpacks it the shop's server access — whether such methods stay in the SDK is decided in
     * DEV-4723.
     *
     * @throws SdkException with [SdkError.InvalidArgument] for an empty [shopSecret], an empty id or no id at
     * all, before any request; [SdkError.Http] `403` for a wrong secret, `400` for a customer the server does
     * not find; [SdkError.Decoding] for a `2xx` answer that is not a success; otherwise as [get].
     */
    suspend fun clear(
        shopSecret: String,
        email: String? = null,
        phone: String? = null,
        loyaltyId: String? = null,
        externalId: String? = null,
        telegramId: String? = null,
    )

    /** [clear] for Java and for code outside coroutines: [listener] gets its result once, on the main thread. */
    fun clear(
        shopSecret: String,
        email: String?,
        phone: String?,
        loyaltyId: String?,
        externalId: String?,
        telegramId: String?,
        listener: CompletionListener<Unit>,
    )
}
