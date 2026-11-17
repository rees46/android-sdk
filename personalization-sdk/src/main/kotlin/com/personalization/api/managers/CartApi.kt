package com.personalization.api.managers

import com.personalization.api.responses.cart.CartItem
import com.personalization.core.BridgeApi
import com.personalization.core.CompletionListener
import com.personalization.core.SdkError
import com.personalization.core.SdkException

/**
 * The customer's cart on the server — the `cart` namespace: `sdk.cart.get()`. The server builds the cart
 * from the cart events of [TrackingApi] (`addToCart`, `syncCart`, `removeFromCart`) and finds it by the
 * device's `did`; the SDK adds `did` and the other common fields itself.
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
}
