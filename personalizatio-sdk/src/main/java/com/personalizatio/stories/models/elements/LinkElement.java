package com.personalizatio.stories.models.elements;

import androidx.annotation.NonNull;

import org.json.JSONObject;

public interface LinkElement extends Element {

    default String getLink(@NonNull JSONObject json) {
        var link = json.optString("link_android", "");
        if (link.isEmpty()) {
            link = json.optString("link", "");
        }
        return link;
    }

    String getLink();
}
