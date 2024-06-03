package com.personalizatio.stories.models.elements;

import androidx.annotation.NonNull;

import org.json.JSONObject;

import java.util.Objects;

public class ButtonElement implements LinkElement {

    private final String title;
    private final String background;
    private final String color;
    private final boolean textBold;
    private final String link;

    private static final String DEFAULT_BACKGROUND = "#FFFFFF";
    private static final String DEFAULT_COLOR = "#000000";

    public ButtonElement(@NonNull JSONObject json) {
        title = json.optString("title", "");
        background = json.optString("background", DEFAULT_BACKGROUND);
        color = json.optString("color", DEFAULT_COLOR);
        textBold = json.optBoolean("text_bold", false);
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

    public boolean getTextBold() {
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
        return String.format("ButtonElement{title='%s', background='%s', color='%s', textBold=%b, link='%s'}",
                title, background, color, textBold, link);
    }
}
