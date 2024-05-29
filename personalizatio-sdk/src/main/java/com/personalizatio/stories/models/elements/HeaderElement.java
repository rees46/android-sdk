package com.personalizatio.stories.models.elements;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

public class HeaderElement implements LinkElement {

    private String title;
    private String subtitle;
    private final String link;
    private String icon;

    public HeaderElement(@NonNull JSONObject json) throws JSONException {
        if (json.has("icon")) {
            icon = json.getString("icon");
        }
        if (json.has("title")) {
            title = json.getString("title");
        }
        if (json.has("subtitle")) {
            subtitle = json.getString("subtitle");
        }
        link = getLink(json);
    }

    public String getIcon() {
        return icon;
    }

    public String getTitle() {
        return title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    @Override
    public String getLink() {
        return link;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HeaderElement that)) return false;
        return title.equals(that.title)
                && subtitle.equals(that.subtitle)
                && link.equals(that.link)
                && icon.equals(that.icon);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, subtitle, link, icon);
    }

    @NonNull
    @Override
    public String toString() {
        return "HeaderElement{" +
                "title='" + title + '\'' +
                ", subtitle='" + subtitle + '\'' +
                ", link='" + link + '\'' +
                ", icon='" + icon + '\'' +
                '}';
    }
}
