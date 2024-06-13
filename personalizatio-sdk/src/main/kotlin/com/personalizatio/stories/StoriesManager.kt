package com.personalizatio.stories

import android.util.Log
import com.personalizatio.Params.RecommendedBy
import com.personalizatio.SDK
import com.personalizatio.api.OnApiCallbackListener
import com.personalizatio.stories.models.Story
import com.personalizatio.stories.views.StoriesView
import org.json.JSONException
import org.json.JSONObject

internal class StoriesManager(val sdk: SDK) {

    private lateinit var storiesView: StoriesView

    internal fun initialize(storiesView: StoriesView) {
        this.storiesView = storiesView

        updateStories()
    }

    internal fun showStory(storyId: Int) {
        val show = storiesView.showStory(storyId)

        if (show) return

        sdk.getAsync(String.format(REQUEST_STORY_METHOD, storyId), JSONObject(), object : OnApiCallbackListener() {
            override fun onSuccess(response: JSONObject?) {
                Log.d("story", response.toString())
                response?.let {
                    val story = Story(response)
                    storiesView.showStory(story)
                }
            }
        })
    }

    internal fun requestStories(code: String, listener: OnApiCallbackListener) {
        sdk.getAsync(String.format(REQUEST_STORIES_METHOD, code), JSONObject(), listener)
    }

    internal fun trackStory(event: String, code: String, storyId: Int, slideId: String) {
        try {
            val params = JSONObject()
            params.put("event", event)
            params.put("story_id", storyId)
            params.put("slide_id", slideId)
            params.put("code", code)

            //Запоминаем последний клик в сторис, чтобы при вызове события просмотра товара добавить
            sdk.lastRecommendedBy = RecommendedBy(RecommendedBy.TYPE.STORIES, code)

            sdk.sendAsync(TRACK_STORIES_METHOD, params, null)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    private fun updateStories() {
        requestStories(storiesView.code, object : OnApiCallbackListener() {
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
        })
    }

    private fun getStories(json: JSONObject): List<Story> {
        val stories = ArrayList<Story>()

        val jsonStories = json.getJSONArray("stories")

        for (i in 0 until jsonStories.length()) {
            stories.add(Story(jsonStories.getJSONObject(i)))
        }

        return stories
    }

    companion object {
        private const val TRACK_STORIES_METHOD = "track/stories"
        private const val REQUEST_STORIES_METHOD = "stories/%s"
        private const val REQUEST_STORY_METHOD = "story/%s"
    }
}
