package com.personalizatio.stories.models;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

final public class Slide implements Serializable {
    private String id;
    private String background;
    private String background_color;
    private String preview;
    private String type;
    private List<Element> elements;
    private long duration;
    private boolean prepared = false;

    public Slide(@NonNull JSONObject json) throws JSONException {
        if (json.has("id")) {
            id = json.getString("id");
        }
        if (json.has("background")) {
            background = json.getString("background");
        }
        if (json.has("background_color")) {
            background_color = json.getString("background_color");
        }
        if (json.has("preview")) {
            preview = json.getString("preview");
        }
        if (json.has("type")) {
            type = json.getString("type");
        }
        if (json.has("duration")) {
            duration = json.optLong("duration", 5) * 1000L;
        }
        elements = new ArrayList<>();
        if (json.has("elements")) {
            JSONArray json_elements = json.getJSONArray("elements");
            for( int i = 0; i < json_elements.length(); i++ ) {
                elements.add(new Element(json_elements.getJSONObject(i)));
            }
        }
    }

    public String getId() {
        return id;
    }

    public String getBackground() {
        return background;
    }

    public String getBackgroundColor() {
        return background_color;
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
}
