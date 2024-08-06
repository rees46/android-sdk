package com.personalization.stories.models.elements

import org.json.JSONObject
import java.util.Objects

class HeaderElement(json: JSONObject) : LinkElement {
    val title: String = json.optString("title", "")
    val subtitle: String = json.optString("subtitle", "")
    val icon: String = json.optString("icon", "")
    override val link: String = getLink(json)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HeaderElement) return false
        return title == other.title
                && subtitle == other.subtitle
                && link == other.link
                && icon == other.icon
    }

    override fun hashCode(): Int {
        return Objects.hash(title, subtitle, link, icon)
    }

    override fun toString(): String {
        return "HeaderElement{title='$title', subtitle='$subtitle', link='$link', icon='$icon'}"
    }
}
