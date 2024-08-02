package com.personalization.stories.models.elements

import org.json.JSONObject
import java.util.Objects

class TextBlockElement(json: JSONObject) : Element {
    val isBold: Boolean = json.optBoolean("bold", false)
    val isItalic: Boolean = json.optBoolean("italic", false)
    val textInput: String = json.optString("text_input", "")
    val yOffset: Int = json.optInt("y_offset", 0)
    val fontType: String = json.optString("font_type", "")
    val fontSize: Int = json.optInt("font_size", DEFAULT_FONT_SIZE)
    val textAlign: String = json.optString("text_align", "")
    val textColor: String = json.optString("text_color", "")
    val textLineSpacing: Double = json.optDouble("text_line_spacing", 0.0)
    val textBackgroundColor: String = json.optString("text_background_color", DEFAULT_TEXT_BACKGROUND_COLOR)
    val textBackgroundColorOpacity: String = json.optString("text_background_color_opacity", DEFAULT_TEXT_BACKGROUND_COLOR_OPACITY)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TextBlockElement) return false
        return yOffset == other.yOffset
                && fontSize == other.fontSize
                && textLineSpacing.compareTo(other.textLineSpacing) == 0
                && isBold == other.isBold
                && isItalic == other.isItalic
                && textInput == other.textInput
                && fontType == other.fontType
                && textAlign == other.textAlign
                && textColor == other.textColor
                && textBackgroundColor == other.textBackgroundColor
                && textBackgroundColorOpacity == other.textBackgroundColorOpacity
    }

    override fun hashCode(): Int {
        return Objects.hash(isBold, isItalic, textInput, yOffset, fontType, fontSize, textAlign, textColor, textLineSpacing, textBackgroundColor, textBackgroundColorOpacity)
    }

    override fun toString(): String {
        return "TextBlockElement{bold=$isBold, italic=$isItalic, textInput='$textInput', yOffset=$yOffset, fontType='$fontType', fontSize=$fontSize, textAlign='$textAlign', textColor='$textColor', textLineSpacing=$textLineSpacing, textBackgroundColor='$textBackgroundColor', textBackgroundColorOpacity='$textBackgroundColorOpacity'}"
    }

    companion object {
        private const val DEFAULT_FONT_SIZE = 14
        private const val DEFAULT_TEXT_BACKGROUND_COLOR = "#FFFFFF"
        private const val DEFAULT_TEXT_BACKGROUND_COLOR_OPACITY = "0%"
    }
}
