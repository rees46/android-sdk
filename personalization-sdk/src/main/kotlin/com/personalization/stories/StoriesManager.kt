package com.personalization.stories

import com.personalization.logs.SdkLogs
import com.personalization.logs.LogCodes
import com.personalization.LogLevel
import android.os.Handler
import android.os.Looper
import com.personalization.SDK
import com.personalization.api.OnApiCallbackListener
import com.personalization.core.internal.ArgumentRefusals
import com.personalization.sdk.domain.usecases.network.SendNetworkMethodUseCase
import com.personalization.sdk.domain.usecases.trackingSource.SetTrackingSourceUseCase
import com.personalization.stories.models.Story
import com.personalization.stories.views.StoriesView
import java.lang.ref.WeakReference
import javax.inject.Inject
import org.json.JSONException
import org.json.JSONObject

/**
 * Stateless stories service: it fetches and parses blocks and tracks events, but does not own any
 * view. Each [StoriesView] loads its own block through [loadStories] and renders itself, so several
 * blocks on one screen no longer contend for a single held reference.
 *
 * The only reference kept is a weak pointer to the most recently attached view, used solely as the
 * presentation surface for the view-less [SDK.showStories] entry point — never for loading data.
 */
class StoriesManager @Inject constructor(
    val setTrackingSourceUseCase: SetTrackingSourceUseCase,
    val sendNetworkMethodUseCase: SendNetworkMethodUseCase
) {

    private var lastAttachedView: WeakReference<StoriesView>? = null

    /** The shop of the instance, so the stories' log lines pass that shop's level. */
    @Volatile
    internal var logShopId: String? = null

    /** The block the SDK last requested — the fallback for tracking calls that omit the code. */
    @Volatile
    private var lastRequestedCode: String? = null

    /** Records the surface [SDK.showStories] presents from. Not used for loading. */
    internal fun rememberAttachedView(storiesView: StoriesView) {
        lastAttachedView = WeakReference(storiesView)
    }

    /** Fetches and parses the block for [code], delivering the stories to [onLoaded]. Stateless. */
    internal fun loadStories(code: String, onLoaded: (List<Story>) -> Unit) {
        requestStories(
            code = code,
            listener = object : OnApiCallbackListener() {
                override fun onSuccess(response: JSONObject?) {
                    response ?: return
                    try {
                        onLoaded(getStories(response))
                    } catch (e: JSONException) {
                        SdkLogs.log(LogLevel.ERROR, LogCodes.STORIES, "Stories failed", logShopId, throwable = e)
                    }
                }
            }
        )
    }

    /** Whether a `StoriesView` is attached and still there: the old [showStories] shows the block in it. */
    internal fun hasAttachedView(): Boolean = lastAttachedView?.get() != null

    internal fun showStories(looper: Looper, code: String) {
        val view = lastAttachedView?.get()
        if (view == null) {
            SdkLogs.log(LogLevel.WARNING, LogCodes.STORIES, "showStories: no StoriesView is attached to present from", logShopId, mapOf("code" to code))
            return
        }
        requestStories(
            code = code,
            listener = object : OnApiCallbackListener() {
                override fun onSuccess(response: JSONObject?) {
                    response?.let {
                        try {
                            val stories = getStories(response)

                            if (stories.isEmpty()) return

                            resetStoriesStartPositions(stories)

                            showStories(looper, view, stories)
                        } catch (e: JSONException) {
                            SdkLogs.log(LogLevel.ERROR, LogCodes.STORIES, "Stories failed", logShopId, throwable = e)
                        }
                    }
                }
            }
        )
    }

    internal fun requestStories(code: String, listener: OnApiCallbackListener) {
        lastRequestedCode = code
        sendNetworkMethodUseCase.getAsync(
            method = String.format(REQUEST_STORIES_METHOD, code),
            params = JSONObject(),
            listener = listener
        )
    }

    /**
     * Triggers a story event
     * Also remember the last click in stories in order to add it when calling the product view event
     *
     * @param event Event
     * @param code Stories block code
     * @param storyId Story ID
     * @param slideId Slide ID
     */
    internal fun trackStory(event: String, code: String, storyId: Int, slideId: String) {
        trackStory(
            event = event,
            code = code,
            storyId = storyId.toString(),
            slideId = slideId,
            listener = null
        )
    }

    /**
     * Story event with the story id as a string — the shape the `tracking` namespace speaks.
     * A numeric id still goes on the wire as a number, so the request is unchanged.
     *
     * [code] falls back to the block the SDK last requested; without either the event cannot be sent,
     * since the backend attributes it to a block. Every path that gives up reports through [listener]
     * rather than returning silently — a caller awaiting the callback (the Flutter bridge does) would
     * otherwise wait forever.
     */
    internal fun trackStory(
        event: String,
        code: String?,
        storyId: String,
        slideId: String,
        listener: OnApiCallbackListener? = null
    ) {
        val effectiveCode = code ?: lastRequestedCode
        if (effectiveCode == null) {
            val message = "trackStory($event): no stories code given and no block loaded yet"
            SdkLogs.log(LogLevel.WARNING, LogCodes.STORIES, message, logShopId)
            refuse(listener, message)
            return
        }
        try {
            val params = JSONObject()
            params.put(EVENT_PARAMS_NAME, event)
            params.put(STORY_ID_PARAMS_NAME, storyId.asWireStoryId())
            params.put(SLIDE_ID_PARAMS_NAME, slideId)
            params.put(CODE_PARAMS_NAME, effectiveCode)

            setTrackingSourceUseCase(type = STORIES_SOURCE_TYPE, code = effectiveCode)

            sendNetworkMethodUseCase.postAsync(TRACK_STORIES_METHOD, params, listener)
        } catch (e: JSONException) {
            SdkLogs.log(LogLevel.ERROR, LogCodes.STORIES, "Stories failed", logShopId, throwable = e)
            refuse(listener, e.message)
        }
    }

    /**
     * A story event that cannot go: `invalidArgument` behind the old code ([ArgumentRefusals]), off the
     * caller's thread on the new core.
     */
    private fun refuse(listener: OnApiCallbackListener?, message: String?) =
        ArgumentRefusals.refuse(listener, CLIENT_VALIDATION_ERROR_CODE, message, offThread = sendNetworkMethodUseCase.queuesEvents)

    /**
     * A numeric id keeps going on the wire as a number, the way `trackStory(storyId: Int)` always sent
     * it — one past `Int.MAX_VALUE` too, since the server's story ids run to 4294967295 (contract
     * `tracking`: `story_id` a number). Converted only when the number renders back to the same text, so
     * ids like `"0123"` — which `toLong()` would turn into `123` — stay strings instead of being silently
     * rewritten.
     */
    private fun String.asWireStoryId(): Any =
        toLongOrNull()?.takeIf { it.toString() == this } ?: this

    private fun getStories(json: JSONObject): List<Story> {
        val stories = ArrayList<Story>()

        val jsonStories = json.getJSONArray("stories")

        for (i in 0 until jsonStories.length()) {
            stories.add(Story(jsonStories.getJSONObject(i)))
        }

        return stories
    }

    private fun resetStoriesStartPositions(stories: List<Story>) {
        for (story in stories) {
            story.startPosition = 0
        }
    }

    private fun showStories(
        looper: Looper,
        storiesView: StoriesView,
        stories: List<Story>,
        startPosition: Int = 0
    ) {
        val handler = Handler(looper)
        handler.post {
            storiesView.showStories(
                stories = stories,
                startPosition = startPosition
            )
        }
    }

    companion object {
        const val TRACK_STORIES_METHOD = "track/stories"
        const val REQUEST_STORIES_METHOD = "stories/%s"

        const val EVENT_PARAMS_NAME = "event"
        const val STORY_ID_PARAMS_NAME = "story_id"
        const val SLIDE_ID_PARAMS_NAME = "slide_id"
        const val CODE_PARAMS_NAME = "code"

        /**
         * Reported when the SDK rejects a story event before it reaches the network — same negative
         * code the other client-side validations use, so a caller can tell "we never sent this" from
         * an HTTP status.
         */
        const val CLIENT_VALIDATION_ERROR_CODE: Int = -1

        /** Wire value a story block is attributed by. Matches iOS `TrackingSourceType.stories`. */
        const val STORIES_SOURCE_TYPE = "stories"
    }
}
