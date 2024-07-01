package com.personalizatio.stories;

import android.util.Log;

import com.personalizatio.SDK;
import com.personalizatio.stories.models.Story;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class StoriesUtils {

    public static List<Story> GetStories(JSONObject storiesResponse) {
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
