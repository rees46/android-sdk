package com.personalization.stories

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.personalization.SDK
import com.personalization.api.OnApiCallbackListener
import com.personalization.sdk.domain.models.RecommendedBy
import com.personalization.sdk.domain.usecases.network.SendNetworkMethodUseCase
import com.personalization.sdk.domain.usecases.recommendation.SetRecommendedByUseCase
import com.personalization.stories.models.Story
import com.personalization.stories.views.StoriesView
import javax.inject.Inject
import org.json.JSONException
import org.json.JSONObject

class StoriesManager @Inject constructor(
    val setRecommendedByUseCase: SetRecommendedByUseCase,
    val sendNetworkMethodUseCase: SendNetworkMethodUseCase
) {

    private lateinit var storiesView: StoriesView

    internal fun initialize(storiesView: StoriesView, sdk: SDK) {
        Log.d("StoriesManager", "initialize called")
        this.storiesView = storiesView
        storiesView.sdk = sdk
        updateStories()
    }

    internal fun showStories(looper: Looper, code: String) {
        requestStories(
            code = code,
            listener = object : OnApiCallbackListener() {
                override fun onSuccess(response: JSONObject?) {
                    response?.let {
                        Log.d("stories", response.toString())
                        try {
                            val stories = getStories(response)

                            if (stories.isEmpty()) return

                            resetStoriesStartPositions(stories)

                            showStories(looper, stories)
                        } catch (e: JSONException) {
                            Log.e(SDK.TAG, e.message, e)
                        }
                    }
                }
            }
        )
    }

    internal fun requestStories(code: String, listener: OnApiCallbackListener) {
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
        try {
            val params = JSONObject()
            params.put(EVENT_PARAMS_NAME, event)
            params.put(STORY_ID_PARAMS_NAME, storyId)
            params.put(SLIDE_ID_PARAMS_NAME, slideId)
            params.put(CODE_PARAMS_NAME, code)

            setRecommendedByUseCase(RecommendedBy(RecommendedBy.TYPE.STORIES, code))

            sendNetworkMethodUseCase.postAsync(TRACK_STORIES_METHOD, params, null)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    private fun updateStories() {
        requestStories(
            code = storiesView.code,
            listener = object : OnApiCallbackListener() {
                override fun onSuccess(response: JSONObject?) {
                    response?.let {
                        Log.d("stories", response.toString())
                        try {
                            val stories = getStories(response)
                            storiesView.updateStories(stories)
                        } catch (e: JSONException) {
                            Log.e(SDK.TAG, e.message, e)
                        }
                    }
                }
            }
        )
    }

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

    private fun showStories(looper: Looper, stories: List<Story>, startPosition: Int = 0) {
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
    }
}
