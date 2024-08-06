package com.personalization.stories.models.elements

import org.json.JSONObject
import java.util.Objects

class ButtonElement(json: JSONObject) : LinkElement {
    val title: String = json.optString("title", "")
    val background: String = json.optString("background", DEFAULT_BACKGROUND)
    val color: String = json.optString("color", DEFAULT_COLOR)
    val textBold: Boolean = json.optBoolean("text_bold", false)
    override val link: String = getLink(json)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ButtonElement) return false
        return textBold == other.textBold
                && title == other.title
                && background == other.background
                && color == other.color
                && link == other.link
    }

    override fun hashCode(): Int {
        return Objects.hash(title, background, color, textBold, link)
    }

    override fun toString(): String {
        return "ButtonElement{title='$title', background='$background', color='$color', textBold=$textBold, link='$link'}"
    }

    companion object {
        private const val DEFAULT_BACKGROUND = "#FFFFFF"
        private const val DEFAULT_COLOR = "#000000"
    }
}
