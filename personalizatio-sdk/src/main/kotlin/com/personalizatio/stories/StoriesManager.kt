package com.personalizatio.stories

import android.util.Log
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

        sdk.getAsync("story/$storyId", JSONObject(), object : OnApiCallbackListener() {
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
        sdk.getAsync("stories/$code", JSONObject(), listener)
    }

    private fun updateStories() {
        storiesView.code?.let { code ->
            requestStories(code, object : OnApiCallbackListener() {
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
    }

    private fun getStories(json: JSONObject): List<Story> {
        val stories = ArrayList<Story>()

        val jsonStories = json.getJSONArray("stories")

        for (i in 0 until jsonStories.length()) {
            stories.add(Story(jsonStories.getJSONObject(i)))
        }

        return stories
    }
}
