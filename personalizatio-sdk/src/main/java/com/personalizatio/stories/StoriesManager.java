package com.personalizatio.stories;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.personalizatio.Api;
import com.personalizatio.SDK;
import com.personalizatio.stories.models.Story;
import com.personalizatio.stories.views.StoriesView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class StoriesManager {

    private final SDK sdk;
    private StoriesView storiesView;

    private final String REQUEST_STORIES_METHOD = "stories/";

    public StoriesManager(SDK sdk) {
        this.sdk = sdk;
    }

    public void initialize(StoriesView storiesView) {
        this.storiesView = storiesView;

        updateStories();
    }

    public void requestStories(String code, Api.OnApiCallbackListener listener) {
        sdk.getAsync(REQUEST_STORIES_METHOD + code, new JSONObject(), listener);
    }

    public void showStories(String code) {
        requestStories(code, new Api.OnApiCallbackListener() {
            @Override
            public void onSuccess(JSONObject response) {
                var stories = getStories(response);

                if(stories.isEmpty()) return;

                resetStoriesStartPositions(stories);

                showStories(stories);
            }
        });
    }

    private void updateStories() {
        requestStories(storiesView.getCode(), new Api.OnApiCallbackListener() {
            @Override
            public void onSuccess(JSONObject response) {
                Log.d("stories", response.toString());

                var stories = getStories(response);
                storiesView.updateStories(stories);
            }
        });
    }

    private void resetStoriesStartPositions(List<Story> stories) {
        for (var story: stories) {
            story.setStartPosition(0);
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private void showStories(List<Story> stories) {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(() -> storiesView.showStories(stories, 0, () -> {}));
    }

    private static List<Story> getStories(JSONObject storiesResponse) {
        var stories = new ArrayList<Story>();

        try {
            JSONArray json_stories = storiesResponse.getJSONArray("stories");
            for( int i = 0; i < json_stories.length(); i++ ) {
                stories.add(new Story(json_stories.getJSONObject(i)));
            }
        } catch(JSONException e) {
            Log.e(SDK.TAG, e.getMessage(), e);
        }

        return stories;
    }
}
