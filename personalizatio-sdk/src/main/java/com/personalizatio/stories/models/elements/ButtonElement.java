package com.personalizatio.stories.models.elements;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

public class ButtonElement implements LinkElement {

    private String title;
    private String background;
    private String color;
    private Boolean textBold = false;
    private final String link;

    public ButtonElement(@NonNull JSONObject json) throws JSONException {
        if (json.has("title")) {
            title = json.getString("title");
        }
        if (json.has("background")) {
            background = json.getString("background");
        }
        if (json.has("color")) {
            color = json.getString("color");
        }
        if (json.has("text_bold")) {
            textBold = json.getBoolean("text_bold");
        }
        link = getLink(json);
    }

    public String getTitle() {
        return title;
    }

    public String getBackground() {
        return background;
    }

    public String getColor() {
        return color;
    }

    public Boolean getTextBold() {
        return textBold;
    }

    @Override
    public String getLink() {
        return link;
    }
}
