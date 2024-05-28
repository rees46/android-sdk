package com.personalizatio.stories.models.elements;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

public final class TextBlockElement implements Element {
    private Boolean bold = false;
    private Boolean italic = false;
    private String text_input;
    private int y_offset;
    private String font_type;
    private int font_size = 14;
    private String text_align;
    private String text_color;
    private double text_line_spacing;
    private String text_background_color = "#FFFFFF";
    private String text_background_color_opacity = "0%";

    public TextBlockElement(@NonNull JSONObject json) throws JSONException {
        if (json.has("bold")) {
            bold = json.getBoolean("bold");
        }
        if (json.has("italic")) {
            italic = json.getBoolean("italic");
        }
        if (json.has("text_input")) {
            text_input = json.getString("text_input");
        }
        if (json.has("y_offset")) {
            y_offset = json.getInt("y_offset");
        }
        if (json.has("font_type")) {
            font_type = json.getString("font_type");
        }
        if (json.has("font_size")) {
            font_size = json.getInt("font_size");
        }
        if (json.has("text_align")) {
            text_align = json.getString("text_align");
        }
        if (json.has("text_color")) {
            text_color = json.getString("text_color");
        }
        if (json.has("text_line_spacing")) {
            text_line_spacing = json.getDouble("text_line_spacing");
        }
        if (json.has("text_background_color")) {
            text_background_color = json.getString("text_background_color");
        }
        if (json.has("text_background_color_opacity")) {
            text_background_color_opacity = json.getString("text_background_color_opacity");
        }
    }

    public Boolean isBold() {
        return bold;
    }

    public Boolean isItalic() {
        return italic;
    }

    public String getTextInput() {
        return text_input;
    }

    public int getYOffset() {
        return y_offset;
    }

    public String getFontType() {
        return font_type;
    }

    public int getFontSize() {
        return font_size;
    }

    public String getTextAlign() {
        return text_align;
    }

    public String getTextColor() {
        return text_color;
    }

    public double getTextLineSpacing() {
        return text_line_spacing;
    }

    public String getTextBackgroundColor() {
        return text_background_color;
    }

    public String getTextBackgroundColorOpacity() {
        return text_background_color_opacity;
    }
}
