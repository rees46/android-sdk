package com.personalizatio.stories.models.elements;

import androidx.annotation.NonNull;

import org.json.JSONObject;

import java.util.Objects;

public final class TextBlockElement implements Element {

    private final boolean bold;
    private final boolean italic;
    private final String textInput;
    private final int yOffset;
    private final String fontType;
    private final int fontSize;
    private final String textAlign;
    private final String textColor;
    private final double textLineSpacing;
    private final String textBackgroundColor;
    private final String textBackgroundColorOpacity;

    private static final int DEFAULT_FONT_SIZE = 14;
    private static final String DEFAULT_TEXT_BACKGROUND_COLOR = "#FFFFFF";
    private static final String DEFAULT_TEXT_BACKGROUND_COLOR_OPACITY = "0%";

    public TextBlockElement(@NonNull JSONObject json) {
        bold = json.optBoolean("bold", false);
        italic = json.optBoolean("italic", false);
        textInput = json.optString("text_input", "");
        yOffset = json.optInt("y_offset", 0);
        fontType = json.optString("font_type", "");
        fontSize = json.optInt("font_size", DEFAULT_FONT_SIZE);
        textAlign = json.optString("text_align", "");
        textColor = json.optString("text_color", "");
        textLineSpacing = json.optDouble("text_line_spacing", 0.0);
        textBackgroundColor = json.optString("text_background_color", DEFAULT_TEXT_BACKGROUND_COLOR);
        textBackgroundColorOpacity = json.optString("text_background_color_opacity", DEFAULT_TEXT_BACKGROUND_COLOR_OPACITY);
    }

    public boolean isBold() {
        return bold;
    }

    public boolean isItalic() {
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
