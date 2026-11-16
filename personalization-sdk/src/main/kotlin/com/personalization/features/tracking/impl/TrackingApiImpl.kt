package com.personalization.features.tracking.impl

import com.personalization.LogLevel
import com.personalization.Params
import com.personalization.Params.TrackEvent
import com.personalization.api.OnApiCallbackListener
import com.personalization.api.managers.TrackEventManager
import com.personalization.api.managers.TrackingApi
import com.personalization.api.models.purchase.PurchaseTrackingRequest
import com.personalization.api.models.tracking.TrackingItem
import com.personalization.api.models.tracking.TrackingSource
import com.personalization.api.models.tracking.putSource
import com.personalization.api.params.ProductItemParams
import com.personalization.core.SdkError
import com.personalization.core.internal.CoreDispatchers
import com.personalization.core.internal.LegacyDelivery
import com.personalization.core.internal.LegacyErrors
import com.personalization.logs.LogCodes
import com.personalization.logs.SdkLogs
import com.personalization.sdk.domain.usecases.trackingSource.SetTrackingSourceUseCase
import com.personalization.stories.StoriesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

/**
 * Implementation of the `tracking` namespace.
 *
 * It owns no logic of its own: every call is translated into the parameters the API expects and
 * handed to the same managers the (deprecated) root-level tracking methods use, so behaviour —
 * popup handling, stored-source attribution, request queueing — is unchanged.
 */
internal class TrackingApiImpl @Inject constructor(
    private val trackEventManager: TrackEventManager,
    private val storiesManager: StoriesManager,
    private val setTrackingSourceUseCase: SetTrackingSourceUseCase,
    /**
     * Whether an empty id or query is refused before it is queued (contract `tracking`: `invalidArgument`).
     * True on the new core, whose queue keeps events; the legacy core sends them as before.
     */
    private val checksArguments: () -> Boolean = { false },
) : TrackingApi {

    /**
     * Refuses a blank [value] of [name] on the new core, before the queue: the listener hears `-1`
     * (`invalidArgument`) on a background thread, as for the core's other refusals. True when refused.
     */
    private fun refused(name: String, value: String, listener: OnApiCallbackListener?): Boolean {
        if (value.isNotBlank() || !checksArguments()) return false
        val message = "$name is empty"
        SdkLogs.log(LogLevel.WARNING, LogCodes.INVALID_ARGUMENT, "A tracking call was refused: $message", null, mapOf("reason" to "empty_$name"))
        listener?.let { refusals.launch { delivery.failure(it, LegacyErrors.NO_ANSWER, message, SdkError.InvalidArgument(message)) } }
        return true
    }

    private val delivery = LegacyDelivery { level, code, message, throwable -> SdkLogs.log(level, code, message, throwable = throwable) }

    /**
     * On the new core, a slide `track/stories` does not take — a story id that is not a positive whole
     * number (a product campaign's virtual slide, `{story_id}_{index}`), or a slide id that is not one in
     * digits — is not sent and is no error (contract `tracking`): the listener hears an empty success.
     */
    private fun virtualSlide(storyId: String, slideId: String, listener: OnApiCallbackListener?): Boolean {
        if (!checksArguments()) return false
        val story = storyId.toLongOrNull()?.takeIf { it > 0 && it.toString() == storyId }
        val slide = slideId.takeIf { id -> id.all { it in '0'..'9' } }?.toLongOrNull()?.takeIf { it > 0 }
        if (story != null && slide != null) return false
        SdkLogs.log(LogLevel.DEBUG, LogCodes.STORIES, "A story event of a virtual slide is not sent", null, mapOf("reason" to "virtual_slide"))
        listener?.let { refusals.launch { delivery.success(it, JSONObject()) } }
        return true
    }

    /** Where a refusal reaches its listener: off the caller's thread, once. */
    private val refusals = CoroutineScope(SupervisorJob() + CoreDispatchers.DEFAULT.io)

    override fun productView(
        itemId: String,
        source: TrackingSource?,
        listener: OnApiCallbackListener?,
    ) {
        if (refused("itemId", itemId, listener)) return
        trackEventManager.track(
            event = TrackEvent.VIEW,
            params = Params().put(ProductItemParams(itemId)).withSource(source),
            listener = listener,
        )
    }

    override fun categoryView(categoryId: String, listener: OnApiCallbackListener?) {
        if (refused("categoryId", categoryId, listener)) return
        trackEventManager.track(
            event = TrackEvent.CATEGORY,
            params = Params().put(Params.Parameter.CATEGORY_ID, categoryId),
            listener = listener,
        )
    }

    override fun search(
        query: String,
        results: List<String>?,
        listener: OnApiCallbackListener?,
    ) {
        if (refused("query", query, listener)) return
        val params = Params().put(Params.Parameter.SEARCH_QUERY, query)
        if (!results.isNullOrEmpty()) {
            params.put(Params.RESULTS_PARAM, results.joinToString(separator = ","))
        }
        trackEventManager.track(event = TrackEvent.SEARCH, params = params, listener = listener)
    }

    override fun addToCart(
        item: TrackingItem,
        source: TrackingSource?,
        listener: OnApiCallbackListener?,
    ) {
        if (refused("itemId", item.id, listener)) return
        trackEventManager.track(
            event = TrackEvent.CART,
            params = Params().put(item.toProductParams()).withSource(source),
            listener = listener,
        )
    }

    override fun syncCart(items: List<TrackingItem>, listener: OnApiCallbackListener?) {
        val params = Params()
        items.forEach { params.put(it.toProductParams()) }
        params.putEmptyItemsIfNone()
        params.put(Params.Parameter.FULL_CART, true)
        trackEventManager.track(event = TrackEvent.CART, params = params, listener = listener)
    }

    override fun removeFromCart(itemId: String, listener: OnApiCallbackListener?) {
        if (refused("itemId", itemId, listener)) return
        trackEventManager.track(
            event = TrackEvent.REMOVE_FROM_CART,
            params = Params().put(ProductItemParams(itemId)),
            listener = listener,
        )
    }

    override fun addToFavorites(
        itemId: String,
        source: TrackingSource?,
        listener: OnApiCallbackListener?,
    ) {
        if (refused("itemId", itemId, listener)) return
        trackEventManager.track(
            event = TrackEvent.WISH,
            params = Params().put(ProductItemParams(itemId)).withSource(source),
            listener = listener,
        )
    }

    override fun syncFavorites(itemIds: List<String>, listener: OnApiCallbackListener?) {
        val params = Params()
        itemIds.forEach { params.put(ProductItemParams(it)) }
        params.putEmptyItemsIfNone()
        params.put(Params.Parameter.FULL_WISH, true)
        trackEventManager.track(event = TrackEvent.WISH, params = params, listener = listener)
    }

    override fun removeFromFavorites(itemId: String, listener: OnApiCallbackListener?) {
        if (refused("itemId", itemId, listener)) return
        trackEventManager.track(
            event = TrackEvent.REMOVE_FROM_WISH,
            params = Params().put(ProductItemParams(itemId)),
            listener = listener,
        )
    }

    override fun storyView(
        storyId: String,
        slideId: String,
        code: String?,
        listener: OnApiCallbackListener?,
    ) {
        if (refused("storyId", storyId, listener) || refused("slideId", slideId, listener)) return
        if (virtualSlide(storyId, slideId, listener)) return
        storiesManager.trackStory(
            event = STORY_VIEW_EVENT,
            code = code,
            storyId = storyId,
            slideId = slideId,
            listener = listener,
        )
    }

    override fun storyClick(
        storyId: String,
        slideId: String,
        code: String?,
        listener: OnApiCallbackListener?,
    ) {
        if (refused("storyId", storyId, listener) || refused("slideId", slideId, listener)) return
        if (virtualSlide(storyId, slideId, listener)) return
        storiesManager.trackStory(
            event = STORY_CLICK_EVENT,
            code = code,
            storyId = storyId,
            slideId = slideId,
            listener = listener,
        )
    }

    override fun purchase(
        request: PurchaseTrackingRequest,
        source: TrackingSource?,
        listener: OnApiCallbackListener?,
    ) {
        // An attribution already set on the request is the more specific one — it was built with the
        // order — so [source] only fills the gap when the caller left it empty. This one is per-call:
        // it colours this order and nothing else, which is why it does not go through setSource.
        val attributed = if (source != null && request.recommendedBy == null) {
            request.copy(recommendedBy = Params.RecommendedBy(source.type.value, source.code))
        } else {
            request
        }
        trackEventManager.trackPurchase(request = attributed, listener = listener)
    }

    override fun custom(
        event: String,
        time: Int?,
        category: String?,
        label: String?,
        value: Int?,
        customFields: Map<String, Any?>?,
        listener: OnApiCallbackListener?,
    ) {
        if (refused("event", event, listener)) return
        trackEventManager.trackEvent(
            event = event,
            time = time,
            category = category,
            label = label,
            value = value,
            customFields = customFields,
            listener = listener,
        )
    }

    override fun popupShown(popupId: Int, listener: OnApiCallbackListener?) {
        trackEventManager.trackPopupShown(popupId = popupId, listener = listener)
    }

    override fun setSource(source: TrackingSource) {
        setTrackingSourceUseCase(type = source.type.value, code = source.code)
    }

    private fun Params.withSource(source: TrackingSource?): Params =
        source?.let { putSource(it) } ?: this

    /**
     * "The cart is now empty" is a real sync, and it has to say so: without this the request would
     * carry `full_cart` and no `items` at all, which reads as "nothing changed". Matches iOS, which
     * always sends the list.
     */
    private fun Params.putEmptyItemsIfNone() {
        if (!build().has(ITEMS_PARAM)) build().put(ITEMS_PARAM, JSONArray())
    }

    private fun TrackingItem.toProductParams(): ProductItemParams {
        val params = ProductItemParams(id).set(ProductItemParams.PARAMETER.AMOUNT, quantity)
        price?.let { params.set(ProductItemParams.PARAMETER.PRICE, it) }
        fashionSize?.let { params.set(ProductItemParams.PARAMETER.FASHION_SIZE, it) }
        return params
    }

    private companion object {
        const val STORY_VIEW_EVENT = "view"
        const val STORY_CLICK_EVENT = "click"
        const val ITEMS_PARAM = "items"
    }
}
