package com.personalization.api.managers

import com.personalization.api.models.purchase.PurchaseTrackingRequest
import com.personalization.api.models.tracking.TrackingItem
import com.personalization.api.models.tracking.TrackingSource
import com.personalization.core.BridgeApi
import com.personalization.core.CompletionListener
import com.personalization.core.SdkError

/**
 * The events of [TrackingApi] as the contract completes them — `tracking.queued` — for the Flutter and React
 * Native bridges: `sdk.tracking.queued.productView("37", listener = …)`.
 *
 * Each method sends what its twin of [TrackingApi] sends, the same way — the same path and fields, the stored
 * source, `recommended_by`, a popup of the event's answer — and refuses what it refuses. Only its [listener]
 * differs: a [CompletionListener] that hears once, on the main thread, that the new core's queue accepted the
 * event (written to disk), not the server's answer, which comes later and is not the call's. A call fails with
 * [SdkError.InvalidArgument] for an argument the twin refuses, before the queue and with nothing sent;
 * [SdkError.ShopBlocked] for a refused shop; [SdkError.NotInitialized] after `release()` and on the legacy
 * core, where nothing is sent. A slide `track/stories` does not take is not sent and is a success, as for the
 * twin.
 *
 * The methods of [TrackingApi] keep their `OnApiCallbackListener`, which hears the server's answer, until the
 * major release that changes its type (contract `tracking`).
 */
@BridgeApi
interface QueuedTrackingApi {

    /** [TrackingApi.productView]. */
    fun productView(itemId: String, source: TrackingSource? = null, listener: CompletionListener<Unit>? = null)

    /** [TrackingApi.categoryView]. */
    fun categoryView(categoryId: String, listener: CompletionListener<Unit>? = null)

    /** [TrackingApi.search]. */
    fun search(query: String, results: List<String>? = null, listener: CompletionListener<Unit>? = null)

    /** [TrackingApi.addToCart]. */
    fun addToCart(item: TrackingItem, source: TrackingSource? = null, listener: CompletionListener<Unit>? = null)

    /** [TrackingApi.syncCart]. */
    fun syncCart(items: List<TrackingItem>, listener: CompletionListener<Unit>? = null)

    /** [TrackingApi.removeFromCart]. */
    fun removeFromCart(itemId: String, listener: CompletionListener<Unit>? = null)

    /** [TrackingApi.addToFavorites]. */
    fun addToFavorites(itemId: String, source: TrackingSource? = null, listener: CompletionListener<Unit>? = null)

    /** [TrackingApi.syncFavorites]. */
    fun syncFavorites(itemIds: List<String>, listener: CompletionListener<Unit>? = null)

    /** [TrackingApi.removeFromFavorites]. */
    fun removeFromFavorites(itemId: String, listener: CompletionListener<Unit>? = null)

    /** [TrackingApi.storyView]. */
    fun storyView(storyId: String, slideId: String, code: String? = null, listener: CompletionListener<Unit>? = null)

    /** [TrackingApi.storyClick]. */
    fun storyClick(storyId: String, slideId: String, code: String? = null, listener: CompletionListener<Unit>? = null)

    /** [TrackingApi.purchase]. */
    fun purchase(request: PurchaseTrackingRequest, source: TrackingSource? = null, listener: CompletionListener<Unit>? = null)

    /** [TrackingApi.custom]. */
    fun custom(
        event: String,
        time: Int? = null,
        category: String? = null,
        label: String? = null,
        value: Int? = null,
        customFields: Map<String, Any?>? = null,
        listener: CompletionListener<Unit>? = null,
    )

    /** [TrackingApi.popupShown]. */
    fun popupShown(popupId: Int, listener: CompletionListener<Unit>? = null)

    /** [TrackingApi.bannerView], which completes the same way already. */
    fun bannerView(bannerId: String, code: String, listener: CompletionListener<Unit>? = null)

    /** [TrackingApi.bannerClick], which completes the same way already. */
    fun bannerClick(bannerId: String, code: String, listener: CompletionListener<Unit>? = null)
}
