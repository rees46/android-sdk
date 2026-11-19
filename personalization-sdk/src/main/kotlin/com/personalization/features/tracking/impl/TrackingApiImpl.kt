package com.personalization.features.tracking.impl

import com.personalization.LogLevel
import com.personalization.Params
import com.personalization.Params.TrackEvent
import com.personalization.api.OnApiCallbackListener
import com.personalization.api.managers.QueuedTrackingApi
import com.personalization.api.managers.TrackEventManager
import com.personalization.api.managers.TrackingApi
import com.personalization.api.models.purchase.PurchaseTrackingRequest
import com.personalization.api.models.tracking.TrackingItem
import com.personalization.api.models.tracking.TrackingSource
import com.personalization.api.models.tracking.TrackingSourceType
import com.personalization.api.models.tracking.putSource
import com.personalization.api.params.ProductItemParams
import com.personalization.core.BridgeApi
import com.personalization.core.CompletionListener
import com.personalization.core.SdkError
import com.personalization.core.SdkException
import com.personalization.core.SdkResult
import com.personalization.core.internal.ArgumentRefusals
import com.personalization.core.internal.CoreDispatchers
import com.personalization.core.internal.CurrentError
import com.personalization.core.internal.EventAnswerListener
import com.personalization.core.internal.LegacyDelivery
import com.personalization.core.internal.LegacyErrors
import com.personalization.core.internal.NamespaceCalls
import com.personalization.core.internal.invalidArgument
import com.personalization.features.trackEvent.impl.PurchaseTrackingJsonBuilder
import com.personalization.logs.LogCodes
import com.personalization.logs.SdkLogs
import com.personalization.sdk.domain.usecases.trackingSource.SetTrackingSourceUseCase
import com.personalization.stories.StoriesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference
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
    /**
     * The path of the bridge-only events, [bannerView], [bannerClick] and [queued]: the new core's queue.
     * Without the instance's new core they fail with `notInitialized`.
     */
    private val events: NamespaceCalls = NamespaceCalls(core = { null }, dispatchers = { CoreDispatchers.DEFAULT }, shopId = { null }),
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
     * Refuses an event's [source] the server does not take in `recommended_by` — [TrackingSourceType.TRANSACTIONAL]
     * and [TrackingSourceType.WEB_PUSH_DIGEST]: it would reject the whole event (contract `tracking`). On both
     * cores the listener hears `onError([code])` behind `invalidArgument` — off the caller's thread on the new
     * core, as its other refusals — and nothing is sent. True when refused.
     */
    private fun refusedSource(source: TrackingSource?, listener: OnApiCallbackListener?, code: Int = LegacyErrors.NO_ANSWER): Boolean {
        val type = source?.type ?: return false
        if (type != TrackingSourceType.TRANSACTIONAL && type != TrackingSourceType.WEB_PUSH_DIGEST) return false
        val message = "source ${type.value} is not accepted for an event"
        SdkLogs.log(LogLevel.WARNING, LogCodes.INVALID_ARGUMENT, "A tracking call was refused: $message", null, mapOf("reason" to "unsupported_source"))
        ArgumentRefusals.refuse(listener, code, message, offThread = checksArguments())
        return true
    }

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
        if (refused("itemId", itemId, listener) || refusedSource(source, listener)) return
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
        if (refused("itemId", item.id, listener) || refusedSource(source, listener)) return
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
        if (refused("itemId", itemId, listener) || refusedSource(source, listener)) return
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
        // The code purchase's other argument errors have.
        if (refusedSource(source, listener, PurchaseTrackingJsonBuilder.CLIENT_VALIDATION_ERROR_CODE)) return
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

    @BridgeApi
    override fun bannerView(bannerId: String, code: String, listener: CompletionListener<Unit>?) =
        banner(BANNER_VIEW, BANNER_VIEW_EVENT, bannerId, code, listener)

    @BridgeApi
    override fun bannerClick(bannerId: String, code: String, listener: CompletionListener<Unit>?) =
        banner(BANNER_CLICK, BANNER_CLICK_EVENT, bannerId, code, listener)

    /**
     * A banner's [event] for `track/slider`: `banner_id` as a number, `recommended_code` the slider's
     * [code]; `recommended_by` the server does not read here. Only through the new core's queue.
     */
    private fun banner(call: String, event: String, bannerId: String, code: String, listener: CompletionListener<Unit>?) =
        events.event(call, SLIDER_PATH, listener) {
            val id = bannerId.takeIf { id -> id.isNotEmpty() && id.all { it in '0'..'9' } }?.toLongOrNull()?.takeIf { it in 1..MAX_BANNER_ID }
                ?: invalidArgument("bannerId is not a positive whole number")
            if (code.isBlank()) invalidArgument("code is empty")
            JSONObject().put(EVENT_PARAM, event).put(BANNER_ID_PARAM, id).put(RECOMMENDED_CODE_PARAM, code)
        }

    /**
     * `tracking.queued`: each method runs its twin above ([queue]), so the event, its checks and what goes
     * with it are the twin's; only its listener hears the queue's acceptance instead of the answer.
     */
    @BridgeApi
    override val queued: QueuedTrackingApi = object : QueuedTrackingApi {
        override fun productView(itemId: String, source: TrackingSource?, listener: CompletionListener<Unit>?) =
            queue("tracking.productView", listener) { this@TrackingApiImpl.productView(itemId, source, it) }

        override fun categoryView(categoryId: String, listener: CompletionListener<Unit>?) =
            queue("tracking.categoryView", listener) { this@TrackingApiImpl.categoryView(categoryId, it) }

        override fun search(query: String, results: List<String>?, listener: CompletionListener<Unit>?) =
            queue("tracking.search", listener) { this@TrackingApiImpl.search(query, results, it) }

        override fun addToCart(item: TrackingItem, source: TrackingSource?, listener: CompletionListener<Unit>?) =
            queue("tracking.addToCart", listener) { this@TrackingApiImpl.addToCart(item, source, it) }

        override fun syncCart(items: List<TrackingItem>, listener: CompletionListener<Unit>?) =
            queue("tracking.syncCart", listener) { this@TrackingApiImpl.syncCart(items, it) }

        override fun removeFromCart(itemId: String, listener: CompletionListener<Unit>?) =
            queue("tracking.removeFromCart", listener) { this@TrackingApiImpl.removeFromCart(itemId, it) }

        override fun addToFavorites(itemId: String, source: TrackingSource?, listener: CompletionListener<Unit>?) =
            queue("tracking.addToFavorites", listener) { this@TrackingApiImpl.addToFavorites(itemId, source, it) }

        override fun syncFavorites(itemIds: List<String>, listener: CompletionListener<Unit>?) =
            queue("tracking.syncFavorites", listener) { this@TrackingApiImpl.syncFavorites(itemIds, it) }

        override fun removeFromFavorites(itemId: String, listener: CompletionListener<Unit>?) =
            queue("tracking.removeFromFavorites", listener) { this@TrackingApiImpl.removeFromFavorites(itemId, it) }

        override fun storyView(storyId: String, slideId: String, code: String?, listener: CompletionListener<Unit>?) =
            queue("tracking.storyView", listener) { this@TrackingApiImpl.storyView(storyId, slideId, code, it) }

        override fun storyClick(storyId: String, slideId: String, code: String?, listener: CompletionListener<Unit>?) =
            queue("tracking.storyClick", listener) { this@TrackingApiImpl.storyClick(storyId, slideId, code, it) }

        override fun purchase(request: PurchaseTrackingRequest, source: TrackingSource?, listener: CompletionListener<Unit>?) =
            queue("tracking.purchase", listener) { this@TrackingApiImpl.purchase(request, source, it) }

        override fun custom(
            event: String,
            time: Int?,
            category: String?,
            label: String?,
            value: Int?,
            customFields: Map<String, Any?>?,
            listener: CompletionListener<Unit>?,
        ) = queue("tracking.custom", listener) {
            this@TrackingApiImpl.custom(event, time, category, label, value, customFields, it)
        }

        override fun popupShown(popupId: Int, listener: CompletionListener<Unit>?) =
            queue("tracking.popupShown", listener) { this@TrackingApiImpl.popupShown(popupId, it) }

        override fun bannerView(bannerId: String, code: String, listener: CompletionListener<Unit>?) =
            this@TrackingApiImpl.bannerView(bannerId, code, listener)

        override fun bannerClick(bannerId: String, code: String, listener: CompletionListener<Unit>?) =
            this@TrackingApiImpl.bannerClick(bannerId, code, listener)
    }

    /**
     * Runs [twin], a method of `tracking`, for the queued method [call], with an [Acceptance] of [listener] for
     * its listener; on the new core only: otherwise [listener] hears `notInitialized`, logged on the legacy core
     * as `core_flag_mismatch`, and nothing is sent. The core decides before the arguments, as in the namespaces.
     */
    private fun queue(call: String, listener: CompletionListener<Unit>?, twin: (OnApiCallbackListener) -> Unit) {
        try {
            events.checked(call) {}
        } catch (e: SdkException) {
            events.tell(listener, SdkResult.Failure(e.error))
            return
        }
        twin(Acceptance(listener))
    }

    /**
     * The listener a queued call hands its twin. [listener] hears once, on the main thread: the queue's
     * acceptance, once the core handed the event to the queue ([queued]); before that, whatever the twin
     * tells — its refusal (`invalidArgument`), the success of a slide that is not sent, `notInitialized` after
     * `release()`. The answer, which comes after the acceptance, is not the call's; a popup in it is shown as
     * for the twin.
     */
    private inner class Acceptance(listener: CompletionListener<Unit>?) : EventAnswerListener() {
        /** Let go once told: the queue keeps this listener until its event is sent, up to 7 days. */
        private val waiting = AtomicReference(listener)

        @Volatile
        private var handed = false

        override fun queued(): (SdkError?) -> Unit {
            handed = true
            return { refused -> complete(if (refused == null) SdkResult.Success(Unit) else SdkResult.Failure(refused)) }
        }

        override fun onSuccess(response: JSONObject?) {
            if (!handed) complete(SdkResult.Success(Unit))
        }

        override fun onSuccess(response: JSONArray) {
            if (!handed) complete(SdkResult.Success(Unit))
        }

        override fun onError(code: Int, msg: String?) {
            // The new core tells every failure with its error; a code alone is a failure without an answer.
            if (!handed) complete(SdkResult.Failure(CurrentError.get() ?: SdkError.Network))
        }

        private fun complete(result: SdkResult<Unit>) {
            waiting.getAndSet(null)?.let { events.tell(it, result) }
        }
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

        const val BANNER_VIEW = "tracking.bannerView"
        const val BANNER_CLICK = "tracking.bannerClick"
        const val SLIDER_PATH = "track/slider"
        const val BANNER_VIEW_EVENT = "view"
        const val BANNER_CLICK_EVENT = "click"
        const val EVENT_PARAM = "event"
        const val BANNER_ID_PARAM = "banner_id"
        const val RECOMMENDED_CODE_PARAM = "recommended_code"

        /** `banner_id` is an unsigned 32-bit number on the server. */
        const val MAX_BANNER_ID = 4_294_967_295L
    }
}
