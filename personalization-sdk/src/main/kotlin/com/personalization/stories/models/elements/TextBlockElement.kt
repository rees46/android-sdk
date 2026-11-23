package com.personalization.stories.models.elements

import com.personalization.ui.widgets.StorySlidePlacement
import java.util.Objects
import org.json.JSONObject

class TextBlockElement(json: JSONObject) : Element {
    val isBold: Boolean = json.optBoolean("bold", false)
    val isItalic: Boolean = json.optBoolean("italic", false)
    val textInput: String = json.optString("text_input", "")
    val yOffset: Int = json.optInt("y_offset", 0)

    /**
     * Stories V2: `y_offset` read as the poll reads it — a number or a number string, 0…100;
     * `null` and not a number — `null`. Not part of equals: V1 uses [yOffset].
     */
    internal val offset: Float? = StorySlidePlacement.offset(json.opt("y_offset"))
    /**
     * Stories V2 on API v3, one rule for every platform: `bold`, without it the legacy `text_bold`; the same
     * for `italic` and `text_italic`; `font_size` only when the feed sent a positive one. Not part of equals:
     * V1 reads [isBold], [isItalic] and [fontSize].
     */
    internal val boldV3: Boolean = flag(json, "bold") ?: flag(json, "text_bold") ?: false
    internal val italicV3: Boolean = flag(json, "italic") ?: flag(json, "text_italic") ?: false
    internal val ownFontSize: Float? = when (val size = json.opt("font_size")) {
        is Number -> size.toFloat()
        is String -> size.trim().toFloatOrNull()
        else -> null
    }?.takeIf { it > 0f && !it.isNaN() && !it.isInfinite() }
    /**
     * Stories V2 on API v3: `text_background_color` and `text_background_color_opacity` as the feed sent them,
     * `null` when it sent none — without the legacy `#FFFFFF` and `0%` defaults. The opacity is "none" as in
     * `calculateOpacity` of the web SDK (`!opacity`): the number 0 and `false` too; only the strings `"0"` and
     * `"0%"` are a transparent 0 %. Not part of equals.
     */
    internal val ownBackgroundColor: String? = raw(json, "text_background_color")
    internal val ownBackgroundOpacity: String? = raw(json, "text_background_color_opacity")
        ?.takeUnless { (json.opt("text_background_color_opacity") as? Number)?.toDouble()?.let { number -> number == 0.0 } == true }
    val fontType: String = json.optString("font_type", "")
    val fontSize: Int = json.optInt("font_size", DEFAULT_FONT_SIZE)
    val textAlign: String = json.optString("text_align", "")
    val textColor: String = json.optString("text_color", "")
    val textLineSpacing: Double = json.optDouble("text_line_spacing", 0.0)
    val promocode: String? = json.optString("promocode", null)
    val textBackgroundColor: String =
        json.optString("text_background_color", DEFAULT_TEXT_BACKGROUND_COLOR)
    val textBackgroundColorOpacity: String =
        json.optString("text_background_color_opacity", DEFAULT_TEXT_BACKGROUND_COLOR_OPACITY)

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
            && promocode == other.promocode
    }

    override fun hashCode(): Int {
        return Objects.hash(
            isBold,
            isItalic,
            textInput,
            yOffset,
            fontType,
            fontSize,
            textAlign,
            textColor,
            textLineSpacing,
            textBackgroundColor,
            textBackgroundColorOpacity,
            promocode
        )
    }

    override fun toString(): String {
        return "TextBlockElement{bold=$isBold, italic=$isItalic, textInput='$textInput', yOffset=$yOffset, fontType='$fontType', fontSize=$fontSize, textAlign='$textAlign', textColor='$textColor', textLineSpacing=$textLineSpacing, textBackgroundColor='$textBackgroundColor', textBackgroundColorOpacity='$textBackgroundColorOpacity', promocode = '$promocode'}"
    }

    companion object {
        /** A string or a number as its text; nothing, `null` or anything else — `null`. */
        private fun raw(json: JSONObject, key: String): String? = when (val value = json.opt(key)) {
            is String -> value
            is Number -> value.toString()
            else -> null
        }

        /** `true`, `1`, `"true"`, `"1"` — on; `false`, `0` and their strings — off; anything else or nothing — `null`. */
        private fun flag(json: JSONObject, key: String): Boolean? = when (val value = json.opt(key)) {
            is Boolean -> value
            is Number -> value.toDouble() != 0.0
            is String -> when (value.trim().lowercase()) {
                "true", "1" -> true
                "false", "0" -> false
                else -> null
            }
            else -> null
        }

        private const val DEFAULT_FONT_SIZE = 14
        private const val DEFAULT_TEXT_BACKGROUND_COLOR = "#FFFFFF"
        private const val DEFAULT_TEXT_BACKGROUND_COLOR_OPACITY = "0%"
    }
}
