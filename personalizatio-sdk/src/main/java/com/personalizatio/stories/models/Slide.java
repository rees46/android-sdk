package com.personalizatio.stories.models;

import androidx.annotation.NonNull;

import com.personalizatio.stories.models.elements.ButtonElement;
import com.personalizatio.stories.models.elements.Element;
import com.personalizatio.stories.models.elements.HeaderElement;
import com.personalizatio.stories.models.elements.ProductElement;
import com.personalizatio.stories.models.elements.ProductsElement;
import com.personalizatio.stories.models.elements.TextBlockElement;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final public class Slide implements Serializable {

    private String id;
    private String background;
    private String backgroundColor;
    private String preview;
    private String type;
    private final List<Element> elements;
    private long duration;
    private boolean prepared = false;

    private final int DEFAULT_DURATION_SECONDS = 5;

    public Slide(@NonNull JSONObject json) throws JSONException {
        if (json.has("id")) {
            id = json.getString("id");
        }
        if (json.has("background")) {
            background = json.getString("background");
        }
        if (json.has("background_color")) {
            backgroundColor = json.getString("background_color");
        }
        if (json.has("preview")) {
            preview = json.getString("preview");
        }
        if (json.has("type")) {
            type = json.getString("type");
        }
        if (json.has("duration")) {
            duration = json.optLong("duration", DEFAULT_DURATION_SECONDS) * 1000L;
        }
        elements = new ArrayList<>();
        if (json.has("elements")) {
            JSONArray json_elements = json.getJSONArray("elements");
            for (int i = 0; i < json_elements.length(); i++) {
                var element = createElement(json_elements.getJSONObject(i));
                if (element != null) {
                    elements.add(element);
                }
            }
        }
    }

    private Element createElement(JSONObject json) throws JSONException {
        if (json.has("type")) {
            var type = json.getString("type");

            switch (type) {
                case "text_block": {
                    return new TextBlockElement(json);
                }
                case "header": {
                    return new HeaderElement(json);
                }
                case "products": {
                    return new ProductsElement(json);
                }
                case "product": {
                    return new ProductElement(json);
                }
                case "button": {
                    return new ButtonElement(json);
                }
            }
        }

        return null;
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
        return "Slide{" +
                "id='" + id + '\'' +
                ", background='" + background + '\'' +
                ", backgroundColor='" + backgroundColor + '\'' +
                ", preview='" + preview + '\'' +
                ", type='" + type + '\'' +
                ", elements=" + elements +
                ", duration=" + duration +
                ", prepared=" + prepared +
                ", DEFAULT_DURATION_SECONDS=" + DEFAULT_DURATION_SECONDS +
                '}';
    }
}
