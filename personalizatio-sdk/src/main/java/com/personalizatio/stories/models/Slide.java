package com.personalizatio.stories.models;

import androidx.annotation.NonNull;

import com.personalizatio.stories.models.elements.ButtonElement;
import com.personalizatio.stories.models.elements.Element;
import com.personalizatio.stories.models.elements.HeaderElement;
import com.personalizatio.stories.models.elements.ProductElement;
import com.personalizatio.stories.models.elements.ProductsElement;
import com.personalizatio.stories.models.elements.TextBlockElement;

import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final public class Slide implements Serializable {

    private final String id;
    private final String background;
    private final String backgroundColor;
    private final String preview;
    private final String type;
    private final List<Element> elements;
    private long duration;
    private boolean prepared = false;

    private static final int DEFAULT_DURATION_SECONDS = 5;
    private static final String DEFAULT_BACKGROUND_COLOR = "#000000";

    public Slide(@NonNull JSONObject json) {
        id = json.optString("id", "");
        background = json.optString("background", "");
        backgroundColor = json.optString("background_color", DEFAULT_BACKGROUND_COLOR);
        preview = json.optString("preview", "");
        type = json.optString("type", "");
        duration = json.optLong("duration", DEFAULT_DURATION_SECONDS) * 1000L;
        elements = new ArrayList<>();
        var elementsJsonArray = json.optJSONArray("elements");
        if (elementsJsonArray != null) {
            for (int i = 0; i < elementsJsonArray.length(); i++) {
                var element = createElement(elementsJsonArray.optJSONObject(i));
                if (element != null) {
                    elements.add(element);
                }
            }
        }
    }

    private static Element createElement(JSONObject json) {
        var type = json.optString("type", null);

        return switch (type) {
            case "text_block" -> new TextBlockElement(json);
            case "header" -> new HeaderElement(json);
            case "products" -> new ProductsElement(json);
            case "product" -> new ProductElement(json);
            case "button" -> new ButtonElement(json);
            default -> null;
        };
    }

    public String getId() {
        return id;
    }

    public String getBackground() {
        return background;
    }

    public String getBackgroundColor() {
        return backgroundColor;
    }

    public String getPreview() {
        return preview;
    }

    public String getType() {
        return type;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public List<Element> getElements() {
        return elements;
    }

    public boolean isPrepared() {
        return prepared;
    }

    public void setPrepared(boolean prepared) {
        this.prepared = prepared;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Slide slide)) return false;
        return duration == slide.duration
                && prepared == slide.prepared
                && id.equals(slide.id)
                && background.equals(slide.background)
                && backgroundColor.equals(slide.backgroundColor)
                && preview.equals(slide.preview)
                && type.equals(slide.type)
                && elements.equals(slide.elements);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, background, backgroundColor, preview, type, elements, duration, prepared, DEFAULT_DURATION_SECONDS);
    }

    @NonNull
    @Override
    public String toString() {
        return String.format("Slide{id='%s', background='%s', backgroundColor='%s', preview='%s', type='%s', elements=%s, duration=%d, prepared=%b}",
                id, background, backgroundColor, preview, type, elements, duration, prepared);
    }
}
