package com.personalizatio.stories.models.elements;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

public class ButtonElement implements LinkElement {

    private String title;
    private String background;
    private String color;
    private boolean textBold = false;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ButtonElement that)) return false;
        return textBold == that.textBold
                && title.equals(that.title)
                && background.equals(that.background)
                && color.equals(that.color)
                && link.equals(that.link);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, background, color, textBold, link);
    }

    @NonNull
    @Override
    public String toString() {
        return "ButtonElement{" +
                "title='" + title + '\'' +
                ", background='" + background + '\'' +
                ", color='" + color + '\'' +
                ", textBold=" + textBold +
                ", link='" + link + '\'' +
                '}';
    }
}
