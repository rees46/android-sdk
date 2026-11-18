package com.personalization.api.managers

import com.personalization.api.models.subscriptions.BackInStockProperties
import com.personalization.api.models.subscriptions.SubscriptionStatus
import com.personalization.api.models.subscriptions.SubscriptionsUpdateRequest
import com.personalization.core.BridgeApi
import com.personalization.core.CompletionListener
import com.personalization.core.SdkError
import com.personalization.core.SdkException

/**
 * The customer's subscriptions — the `subscriptions` namespace: to channels and kinds of mailings, and to a
 * product's price drop or its return to stock. The API finds the customer by the device's `did`, which the
 * SDK adds with the other common fields itself. A contact (`email`, `phone`) tells which address or number
 * a subscription is for, and the server applies the call to the contact's owner: pass one only when it is
 * the customer's own. The SDK leaves checking a contact's format to the server; an empty one is
 * `invalidArgument`.
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
interface SubscriptionsApi {

    /** Subscribes the customer to channels and kinds of mailings, or unsubscribes (`POST /subscriptions/manage`). */
    suspend fun manage(request: SubscriptionsUpdateRequest)

    /**
     * The customer's subscriptions on every channel (`GET /subscriptions/check`); `http(400)` for a `did`
     * the server does not know.
     */
    suspend fun check(): SubscriptionStatus

    /**
     * Subscribes the customer to a drop of [itemId]'s price (`POST /subscriptions/subscribe_for_product_price`).
     * [currentPrice] must be above 0; the server takes the price from the catalog. The server ends the
     * subscription once it told the customer.
     */
    suspend fun subscribeForPriceDrop(itemId: String, currentPrice: Double, email: String? = null, phone: String? = null)

    /**
     * Unsubscribes the customer from price drops of [itemIds] (`POST /subscriptions/unsubscribe_from_product_price`);
     * an empty list unsubscribes from every product. An empty id in the list is `invalidArgument`.
     */
    suspend fun unsubscribeFromPriceDrop(itemIds: List<String>, email: String? = null, phone: String? = null)

    /**
     * Subscribes the customer to [itemId]'s return to stock, in the variant of [properties]
     * (`POST /subscriptions/subscribe_for_product_available`). The server ends the subscription once it
     * told the customer.
     */
    suspend fun subscribeForBackInStock(
        itemId: String,
        properties: BackInStockProperties? = null,
        email: String? = null,
        phone: String? = null,
    )

    /**
     * Unsubscribes the customer from [itemIds]' return to stock (`POST /subscriptions/unsubscribe_from_product_available`);
     * an empty list unsubscribes from every product. An empty id in the list is `invalidArgument`.
     */
    suspend fun unsubscribeFromBackInStock(itemIds: List<String>, email: String? = null, phone: String? = null)

    /** [manage] for Java and for code outside coroutines: [listener] gets its result once, on the main thread. */
    fun manage(request: SubscriptionsUpdateRequest, listener: CompletionListener<Unit>)

    /** [check] for Java and for code outside coroutines: [listener] gets its result once, on the main thread. */
    fun check(listener: CompletionListener<SubscriptionStatus>)

    /** [subscribeForPriceDrop] for Java and for code outside coroutines: [listener] gets its result once, on the main thread. */
    fun subscribeForPriceDrop(itemId: String, currentPrice: Double, email: String?, phone: String?, listener: CompletionListener<Unit>)

    /** [unsubscribeFromPriceDrop] for Java and for code outside coroutines: [listener] gets its result once, on the main thread. */
    fun unsubscribeFromPriceDrop(itemIds: List<String>, email: String?, phone: String?, listener: CompletionListener<Unit>)

    /** [subscribeForBackInStock] for Java and for code outside coroutines: [listener] gets its result once, on the main thread. */
    fun subscribeForBackInStock(
        itemId: String,
        properties: BackInStockProperties?,
        email: String?,
        phone: String?,
        listener: CompletionListener<Unit>,
    )

    /** [unsubscribeFromBackInStock] for Java and for code outside coroutines: [listener] gets its result once, on the main thread. */
    fun unsubscribeFromBackInStock(itemIds: List<String>, email: String?, phone: String?, listener: CompletionListener<Unit>)
}
