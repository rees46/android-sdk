package com.personalizatio.stories.models.elements;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

public final class TextBlockElement implements Element {

    private boolean bold = false;
    private boolean italic = false;
    private String textInput;
    private int yOffset;
    private String fontType;
    private int fontSize = 14;
    private String textAlign;
    private String textColor;
    private double textLineSpacing;
    private String textBackgroundColor = "#FFFFFF";
    private String textBackgroundColorOpacity = "0%";

    public TextBlockElement(@NonNull JSONObject json) throws JSONException {
        if (json.has("bold")) {
            bold = json.getBoolean("bold");
        }
        if (json.has("italic")) {
            italic = json.getBoolean("italic");
        }
        if (json.has("text_input")) {
            textInput = json.getString("text_input");
        }
        if (json.has("y_offset")) {
            yOffset = json.getInt("y_offset");
        }
        if (json.has("font_type")) {
            fontType = json.getString("font_type");
        }
        if (json.has("font_size")) {
            fontSize = json.getInt("font_size");
        }
        if (json.has("text_align")) {
            textAlign = json.getString("text_align");
        }
        if (json.has("text_color")) {
            textColor = json.getString("text_color");
        }
        if (json.has("text_line_spacing")) {
            textLineSpacing = json.getDouble("text_line_spacing");
        }
        if (json.has("text_background_color")) {
            textBackgroundColor = json.getString("text_background_color");
        }
        if (json.has("text_background_color_opacity")) {
            textBackgroundColorOpacity = json.getString("text_background_color_opacity");
        }
    }

    public Boolean isBold() {
        return bold;
    }

    public Boolean isItalic() {
        return italic;
    }

    public String getTextInput() {
        return textInput;
    }

    public int getYOffset() {
        return yOffset;
    }

    public String getFontType() {
        return fontType;
    }

    public int getFontSize() {
        return fontSize;
    }

    public String getTextAlign() {
        return textAlign;
    }

    public String getTextColor() {
        return textColor;
    }

    public double getTextLineSpacing() {
        return textLineSpacing;
    }

    public String getTextBackgroundColor() {
        return textBackgroundColor;
    }

    public String getTextBackgroundColorOpacity() {
        return textBackgroundColorOpacity;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TextBlockElement that)) return false;
        return yOffset == that.yOffset
                && fontSize == that.fontSize
                && Double.compare(textLineSpacing, that.textLineSpacing) == 0
                && bold == that.bold
                && italic == that.italic
                && textInput.equals(that.textInput)
                && fontType.equals(that.fontType)
                && textAlign.equals(that.textAlign)
                && textColor.equals(that.textColor)
                && textBackgroundColor.equals(that.textBackgroundColor)
                && textBackgroundColorOpacity.equals(that.textBackgroundColorOpacity);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bold, italic, textInput, yOffset, fontType, fontSize, textAlign, textColor, textLineSpacing, textBackgroundColor, textBackgroundColorOpacity);
    }

    @NonNull
    @Override
    public String toString() {
        return "TextBlockElement{" +
                "bold=" + bold +
                ", italic=" + italic +
                ", textInput='" + textInput + '\'' +
                ", yOffset=" + yOffset +
                ", fontType='" + fontType + '\'' +
                ", fontSize=" + fontSize +
                ", textAlign='" + textAlign + '\'' +
                ", textColor='" + textColor + '\'' +
                ", textLineSpacing=" + textLineSpacing +
                ", textBackgroundColor='" + textBackgroundColor + '\'' +
                ", textBackgroundColorOpacity='" + textBackgroundColorOpacity + '\'' +
                '}';
    }
}
