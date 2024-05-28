package com.personalizatio.stories.models.elements;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

public interface LinkElement extends Element {

    default String getLink(@NonNull JSONObject json) throws JSONException {
        String link = null;
        if (json.has("link_android")) {
            link = json.getString("link_android");
        }
        if ((link == null || link.isEmpty()) && json.has("link")) {
            link = json.getString("link");
        }
        return link;
    }

    String getLink();
}
