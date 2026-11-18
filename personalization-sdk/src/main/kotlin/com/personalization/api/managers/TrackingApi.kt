package com.personalization.api.managers

import com.personalization.api.OnApiCallbackListener
import com.personalization.api.models.purchase.PurchaseTrackingRequest
import com.personalization.api.models.tracking.TrackingItem
import com.personalization.api.models.tracking.TrackingSource
import com.personalization.api.models.tracking.TrackingSourceType
import com.personalization.api.models.sliders.SliderBanner
import com.personalization.core.BridgeApi
import com.personalization.core.CompletionListener
import com.personalization.core.SdkError

/**
 * Standard tracking events — the `tracking` namespace of the SDK.
 *
 * Reached through an SDK instance: `Rees46.instance().tracking.productView("sku-1")`.
 *
 * On the new core (`Rees46.initializeBridge`) every event goes through the queue: the method returns at
 * once, and a `listener` is kept until the event is sent — after a launch without a network that may be
 * as long as the queue keeps an event, 7 days — then hears the server's answer, or the error that
 * dropped the event. An event the queue refuses at once (an empty id, a released instance, a refused
 * shop) tells the listener at once. Whatever the listener holds stays alive that long: keep it free of
 * screens and other short-lived objects.
 *
 * An event's own `source` takes every [TrackingSourceType] but [TrackingSourceType.TRANSACTIONAL] and
 * [TrackingSourceType.WEB_PUSH_DIGEST]: the server does not take them in `recommended_by` and would reject
 * the whole event, so on both cores such a call sends nothing and the listener hears `onError` behind
 * [SdkError.InvalidArgument] — off the caller's thread on the new core. [setSource] still takes them.
 * Earlier releases sent such an event, and the server refused it.
 */
interface TrackingApi {

    /**
     * Product page opened (`view`).
     *
     * A [source] of [TrackingSourceType.TRANSACTIONAL] or [TrackingSourceType.WEB_PUSH_DIGEST] is
     * [SdkError.InvalidArgument] and sends nothing (see [TrackingApi]).
     */
    fun productView(
        itemId: String,
        source: TrackingSource? = null,
        listener: OnApiCallbackListener? = null,
    )

    /** Category listing opened (`category`). */
    fun categoryView(
        categoryId: String,
        listener: OnApiCallbackListener? = null,
    )

    /**
     * Search query issued by the user (`search`).
     *
     * Pass [results] when the host runs its own search and knows the ids it showed. They go on the
     * wire as one comma-separated field, so ids must not themselves contain a comma.
     */
    fun search(
        query: String,
        results: List<String>? = null,
        listener: OnApiCallbackListener? = null,
    )

    /** One product added to the cart (`cart`). A [source] is checked as in [productView]. */
    fun addToCart(
        item: TrackingItem,
        source: TrackingSource? = null,
        listener: OnApiCallbackListener? = null,
    )

    /**
     * Full cart contents after a change (`cart` with `full_cart`).
     *
     * Pass an empty list when the cart was emptied — the request then carries an empty `items` list,
     * which is how the backend learns the cart is gone.
     */
    fun syncCart(
        items: List<TrackingItem>,
        listener: OnApiCallbackListener? = null,
    )

    /** One product removed from the cart (`remove_from_cart`). */
    fun removeFromCart(
        itemId: String,
        listener: OnApiCallbackListener? = null,
    )

    /** One product added to favorites (`wish`). A [source] is checked as in [productView]. */
    fun addToFavorites(
        itemId: String,
        source: TrackingSource? = null,
        listener: OnApiCallbackListener? = null,
    )

    /** Full favorites contents after a change (`wish` with `full_wish`). */
    fun syncFavorites(
        itemIds: List<String>,
        listener: OnApiCallbackListener? = null,
    )

    /** One product removed from favorites (`remove_wish`). */
    fun removeFromFavorites(
        itemId: String,
        listener: OnApiCallbackListener? = null,
    )

    /**
     * A story slide was shown (`track/stories`, `view`).
     *
     * [code] is the stories block code; when omitted the SDK uses the code of the block it
     * last loaded.
     */
    fun storyView(
        storyId: String,
        slideId: String,
        code: String? = null,
        listener: OnApiCallbackListener? = null,
    )

    /** A story slide was tapped (`track/stories`, `click`). */
    fun storyClick(
        storyId: String,
        slideId: String,
        code: String? = null,
        listener: OnApiCallbackListener? = null,
    )

    /**
     * Completed order (`purchase`).
     *
     * [source] attributes the order; an attribution already set on [request] wins over it. A [source] is
     * checked as in [productView], with the code of purchase's other argument errors, `-2`.
     */
    fun purchase(
        request: PurchaseTrackingRequest,
        source: TrackingSource? = null,
        listener: OnApiCallbackListener? = null,
    )

    /**
     * Custom event defined by the shop (`push/custom`).
     *
     * [customFields] is the one deliberately free-form field: its entries are sent at the top
     * level and duplicated under `payload`. Reserved keys are rejected.
     */
    fun custom(
        event: String,
        time: Int? = null,
        category: String? = null,
        label: String? = null,
        value: Int? = null,
        customFields: Map<String, Any?>? = null,
        listener: OnApiCallbackListener? = null,
    )

    /**
     * A server popup was shown (`popup/showed`).
     *
     * The SDK reports the popups it shows. Call this only for one you drew yourself after keeping it
     * from the SDK through a [com.personalization.PopupPresentationListener].
     */
    fun popupShown(popupId: Int, listener: OnApiCallbackListener? = null)

    /**
     * Stores the attribution source and attaches it to every event for the next 48 hours.
     *
     * Use it when the source outlives a single call — a user entering the catalog from a recommender
     * block. It is stored per SDK instance and survives a cold start, and it travels in its own
     * `source` field rather than the `recommended_by` a per-call [source] uses. For a single event
     * prefer that per-call parameter. Every [TrackingSourceType] is taken here, the ones an event's own
     * source refuses among them.
     *
     * Same behaviour and same wire shape as iOS.
     */
    fun setSource(source: TrackingSource)

    /**
     * A banner of a slider was shown (`track/slider`, `view`): once, when it first became visible in this
     * showing of the slider. [bannerId] and [code] are the banner's [SliderBanner.id] and [SliderBanner.code]
     * from [SlidersApi.get].
     *
     * For the Flutter and React Native bridges, on the new core only: the event goes through the queue, and
     * [listener] hears once, on the main thread, that the queue accepted it, or why not —
     * [SdkError.InvalidArgument] for a [bannerId] that is not a positive whole number or an empty [code],
     * [SdkError.ShopBlocked], or [SdkError.NotInitialized] after `release()` and on the legacy core, where
     * nothing is sent. Hidden from Java.
     */
    @BridgeApi
    @JvmSynthetic
    fun bannerView(bannerId: String, code: String, listener: CompletionListener<Unit>? = null)

    /** A banner of a slider was tapped (`track/slider`, `click`); otherwise as [bannerView]. Hidden from Java. */
    @BridgeApi
    @JvmSynthetic
    fun bannerClick(bannerId: String, code: String, listener: CompletionListener<Unit>? = null)
}
